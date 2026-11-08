package com.vtmen.backend.service;

import com.vtmen.backend.config.DcsApiProperties;
import com.vtmen.backend.model.OrderModel;
import com.vtmen.backend.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /** A successful code entry waiting for the matching DOOR_CLOSED event. */
    private record PendingVerification(String orderCode, String role, Integer compartmentId,
                                       String resourceId, long createdAtMs) {}

    /** How long a code entry stays valid while waiting for the door to close. */
    private static final long VERIFICATION_TTL_MS = 5 * 60 * 1000L;
    /** DCS sometimes fires DOOR_CLOSED twice for one physical close — ignore repeats in this window. */
    private static final long DOOR_CLOSED_DEDUPE_MS = 5 * 1000L;

    /** Pending verifications, keyed by orderCode (one active entry per order). */
    private final java.util.Map<String, PendingVerification> pendingVerifications = new java.util.concurrent.ConcurrentHashMap<>();
    /** Last processed DOOR_CLOSED per compartment (epoch ms) for de-duplication. */
    private final java.util.Map<Integer, Long> lastDoorClosedAt = new java.util.concurrent.ConcurrentHashMap<>();

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private DcsDestinationRegistry dcsDestinationRegistry;

    @Autowired
    private DcsApiProperties dcsApiProperties;

    @Autowired
    private EmailService emailService;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private RobotDispatchService robotDispatchService;

    // Get order by orderCode
    public Optional<OrderModel> getOrderByOrderCode(String orderCode) {
        return orderRepository.findByOrderCode(orderCode);
    }

    // Get completed orders
    public List<OrderModel> getCompletedOrders() {
        Query query = new Query();
        query.addCriteria(Criteria.where("status").is("delivered"));
        return mongoTemplate.find(query, OrderModel.class);
    }

    // Get cancelled orders
    public List<OrderModel> getCancelledOrders() {
        Query query = new Query();
        query.addCriteria(Criteria.where("status").is("cancelled"));
        return mongoTemplate.find(query, OrderModel.class);
    }

    // Get shipping orders
    public List<OrderModel> getShippingOrders() {
        Query query = new Query();
        query.addCriteria(Criteria.where("status").is("shipping"));
        return mongoTemplate.find(query, OrderModel.class);
    }

    // Get placed orders
    public List<OrderModel> getPlacedOrders() {
        Query query = new Query();
        query.addCriteria(Criteria.where("status").is("placed"));
        return mongoTemplate.find(query, OrderModel.class);
    }

    // Get active (non-completed, non-cancelled) orders
    public List<OrderModel> getActiveOrders() {
        Query query = new Query();
        query.addCriteria(Criteria.where("status").nin("delivered", "cancelled"));
        return mongoTemplate.find(query, OrderModel.class);
    }
    // Get pending orders
    public List<OrderModel> getPendingOrders() {
        Query query = new Query();
        query.addCriteria(Criteria.where("status").is("pending"));
        return mongoTemplate.find(query, OrderModel.class);
    }

    // Get pending order by orderCode
    public Optional<OrderModel> getPendingOrderByOrderCode(String orderCode) {
        return orderRepository.findByOrderCode(orderCode)
                .filter(order -> "pending".equalsIgnoreCase(order.getStatus()));
    }

    public Optional<OrderModel> updateOrderStatusPendingToPlaced(String orderCode, Integer compartmentId) {
        return orderRepository.findByOrderCode(orderCode)
                .filter(order -> "pending".equalsIgnoreCase(order.getStatus()))
                .map(order -> {
                    order.setStatus("placed");
                    if (compartmentId != null) {
                        order.setCompartmentId(compartmentId);
                    }
                    order.setDepositedTime(LocalDateTime.now());
                    OrderModel saved = orderRepository.save(order);
                    publishOrderUpdate(saved);

                    // Khi thêm hàng vào tủ thành công -> gửi mã bí mật tới email
                    if (saved.getEmail() != null && !saved.getEmail().isBlank()) {
                        emailService.sendLockerPlacedConfirmation(
                                saved.getEmail(),
                                saved.getFullName(),
                                saved.getOrderCode(),
                                saved.getPinCode(),
                                saved.getCompartmentId(),
                                saved.getAddress()
                        );
                    }

                    return saved;
                });
    }

    public Optional<OrderModel> acceptDepositByQr(String orderCode) {
        // Accept only if order exists and is not cancelled/delivered.
        // If it's pending, move it to placed to mark it as accepted for deposit.
        return orderRepository.findByOrderCode(orderCode)
                .filter(order -> order.getStatus() == null
                        || (!"cancelled".equalsIgnoreCase(order.getStatus())
                        && !"delivered".equalsIgnoreCase(order.getStatus())))
                .map(order -> {
                    boolean wasPending = (order.getStatus() == null || "pending".equalsIgnoreCase(order.getStatus()));
                    if (wasPending) {
                        order.setStatus("placed");
                    }
                    OrderModel saved = orderRepository.save(order);
                    publishOrderUpdate(saved);

                    // Khi thêm hàng vào tủ thành công -> gửi mã bí mật tới email
                    if (wasPending && saved.getEmail() != null && !saved.getEmail().isBlank()) {
                        emailService.sendLockerPlacedConfirmation(
                                saved.getEmail(),
                                saved.getFullName(),
                                saved.getOrderCode(),
                                saved.getPinCode(),
                                saved.getCompartmentId(),
                                saved.getAddress()
                        );
                    }

                    return saved;
                });
    }

    // DCS deposit-closed: never changes order status.
    // If the order has no compartment_id, requires request compartment_id — sets compartment + deposited time, message "Placed successfully".
    // If the order already has a compartment_id, clears compartment_id and deposited time (locker released).
    public Optional<String> applyDepositClosed(String orderCode, Integer requestCompartmentId, OffsetDateTime closedAt) {
        Optional<OrderModel> eligible = orderRepository.findByOrderCode(orderCode)
                .filter(order -> order.getStatus() == null
                        || (!"cancelled".equalsIgnoreCase(order.getStatus())
                        && !"delivered".equalsIgnoreCase(order.getStatus())));

        if (eligible.isEmpty()) {
            return Optional.empty();
        }

        OrderModel order = eligible.get();

        if (order.getCompartmentId() == null) {
            if (requestCompartmentId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing compartment_id");
            }
            order.setCompartmentId(requestCompartmentId);
            order.setStatus("placed");
            if (closedAt != null) {
                order.setDepositedTime(closedAt.toLocalDateTime());
            } else {
                order.setDepositedTime(LocalDateTime.now());
            }
            OrderModel saved = orderRepository.save(order);
            publishOrderUpdate(saved);

            // Khi thêm hàng vào tủ thành công qua cảm biến đóng cửa -> gửi mã bí mật tới email
            if (saved.getEmail() != null && !saved.getEmail().isBlank()) {
                emailService.sendLockerPlacedConfirmation(
                        saved.getEmail(),
                        saved.getFullName(),
                        saved.getOrderCode(),
                        saved.getPinCode(),
                        saved.getCompartmentId(),
                        saved.getAddress()
                );
            }

            return Optional.of("Placed successfully");
        }

        // Order already had a compartment -> door closed means item is taken out!
        order.setStatus("delivered");
        order.setCompletedTime(closedAt != null ? closedAt.toLocalDateTime() : LocalDateTime.now());
        order.setCompartmentId(null);
        order.setDepositedTime(null);
        OrderModel saved = orderRepository.save(order);
        publishOrderUpdate(saved);
        return Optional.of("Delivered successfully");
    }

    // --- Webhook handlers for Door Closed ---
    public Optional<OrderModel> handleDoorClosed(Integer compartmentId, String capacityResourceId, OffsetDateTime closedAt) {
        return handleDoorClosed(compartmentId, null, capacityResourceId, closedAt);
    }

    /** Pick the matching pending verification for a DOOR_CLOSED event and consume it. */
    private synchronized PendingVerification consumeVerification(Integer compartmentId, String resourceId) {
        long now = System.currentTimeMillis();
        pendingVerifications.values().removeIf(v -> now - v.createdAtMs() > VERIFICATION_TTL_MS);

        // 1. Exact compartment match (newest first)
        Optional<PendingVerification> match = pendingVerifications.values().stream()
                .filter(v -> compartmentId != null && compartmentId.equals(v.compartmentId()))
                .max(java.util.Comparator.comparingLong(PendingVerification::createdAtMs));

        // 2. Fallback: DCS opened a different compartment than we requested.
        //    Take the newest verification from the same cabinet (resourceId) whose compartment is not
        //    already physically confirmed elsewhere.
        if (match.isEmpty()) {
            match = pendingVerifications.values().stream()
                    .filter(v -> resourceId == null || v.resourceId() == null || resourceId.equals(v.resourceId()))
                    .max(java.util.Comparator.comparingLong(PendingVerification::createdAtMs));
            match.ifPresent(v -> log.warn("[DoorClosed] Compartment mismatch: order {} was assigned compartment {} but DCS closed compartment {} → using {}",
                    v.orderCode(), v.compartmentId(), compartmentId, compartmentId));
        }

        match.ifPresent(v -> pendingVerifications.remove(v.orderCode()));
        return match.orElse(null);
    }

    /** Lowest compartment number not occupied by another active order. */
    private int allocateFreeCompartment(String excludeOrderCode) {
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (OrderModel o : orderRepository.findAll()) {
            if (o.getCompartmentId() == null) continue;
            if (Objects.equals(o.getOrderCode(), excludeOrderCode)) continue;
            String s = o.getStatus();
            // Only goods physically in the locker occupy a compartment
            if (!"placed".equalsIgnoreCase(s) && !"shipping".equalsIgnoreCase(s)) continue;
            used.add(o.getCompartmentId());
        }
        for (PendingVerification v : pendingVerifications.values()) {
            if (v.compartmentId() != null && !Objects.equals(v.orderCode(), excludeOrderCode)) {
                used.add(v.compartmentId());
            }
        }
        int c = 1;
        while (used.contains(c)) c++;
        return c;
    }

    public Optional<OrderModel> handleDoorClosed(Integer compartmentId, String orderCode, String capacityResourceId, OffsetDateTime closedAt) {
        // De-duplicate repeated DOOR_CLOSED for the same compartment (DCS fires it twice sometimes)
        if (compartmentId != null) {
            long now = System.currentTimeMillis();
            Long prev = lastDoorClosedAt.put(compartmentId, now);
            if (prev != null && now - prev < DOOR_CLOSED_DEDUPE_MS) {
                log.info("[DoorClosed] Duplicate DOOR_CLOSED for compartment {} within {}ms — ignored", compartmentId, now - prev);
                return Optional.empty();
            }
        }

        // 0. Resolve which order this close belongs to. A status change REQUIRES a prior code entry
        //    (verify-pin) or an explicit orderCode from DCS — a bare door close does nothing.
        String effectiveOrderCode = (orderCode != null && !orderCode.isBlank()) ? orderCode.trim() : null;
        if (effectiveOrderCode != null) {
            pendingVerifications.remove(effectiveOrderCode);
        } else {
            PendingVerification pv = consumeVerification(compartmentId, capacityResourceId);
            if (pv == null) {
                log.info("[DoorClosed] No verified order waiting for compartment {} — ignored", compartmentId);
                return Optional.empty();
            }
            effectiveOrderCode = pv.orderCode();
        }

        // 1. If explicit orderCode resolved, process that specific order
        if (effectiveOrderCode != null && !effectiveOrderCode.isBlank()) {
            Optional<OrderModel> byCode = orderRepository.findByOrderCode(effectiveOrderCode);
            if (byCode.isPresent()) {
                OrderModel order = byCode.get();
                if (capacityResourceId != null && !capacityResourceId.isBlank()) {
                    order.setCapacityResourceId(capacityResourceId);
                }
                // Only treat as customer goods-out if status is explicitly placed or shipping
                if ("shipping".equalsIgnoreCase(order.getStatus())
                        || "placed".equalsIgnoreCase(order.getStatus())) {
                    boolean wasShipping = "shipping".equalsIgnoreCase(order.getStatus());
                    order.setStatus("delivered");
                    order.setCompartmentId(null);
                    order.setCompletedTime(closedAt != null ? closedAt.toLocalDateTime() : LocalDateTime.now());
                    OrderModel saved = orderRepository.save(order);
                    publishOrderUpdate(saved);
                    log.info("[DoorClosed] Order {} delivered (completed) via resolved orderCode", order.getOrderCode());

                    // If order was shipping, check if robot has any remaining shipments or should return to charger
                    if (wasShipping) {
                        handlePostDeliveryTaskCleanup(saved);
                    }
                    return Optional.of(saved);
                } else {
                    if (!"pending".equalsIgnoreCase(order.getStatus())) {
                        log.info("[DoorClosed] Order {} has status {} — no transition", order.getOrderCode(), order.getStatus());
                        return Optional.empty();
                    }
                    order.setStatus("placed");
                    // The compartment that physically closed is the source of truth
                    if (compartmentId != null) {
                        order.setCompartmentId(compartmentId);
                    } else if (order.getCompartmentId() == null) {
                        order.setCompartmentId(1);
                    }
                    order.setDepositedTime(closedAt != null ? closedAt.toLocalDateTime() : LocalDateTime.now());
                    OrderModel saved = orderRepository.save(order);
                    publishOrderUpdate(saved);
                    if (saved.getEmail() != null && !saved.getEmail().isBlank()) {
                        emailService.sendLockerPlacedConfirmation(
                                saved.getEmail(),
                                saved.getFullName(),
                                saved.getOrderCode(),
                                saved.getPinCode(),
                                saved.getCompartmentId(),
                                saved.getAddress()
                        );
                    }
                    log.info("[DoorClosed] Order {} deposited via resolved orderCode", order.getOrderCode());
                    return Optional.of(saved);
                }
            }
        }

        log.warn("[DoorClosed] Order {} not found", effectiveOrderCode);
        return Optional.empty();
    }

    /**
     * Legacy compartment-only resolution (no code entry). Kept for reference but no longer used by
     * handleDoorClosed: transitions now require a verify-pin first.
     */
    @SuppressWarnings("unused")
    private Optional<OrderModel> legacyHandleDoorClosedByCompartment(Integer compartmentId, String capacityResourceId, OffsetDateTime closedAt) {
        if (compartmentId == null) return Optional.empty();

        // 2. Query orders in this compartment and filter ONLY ACTIVE orders (not delivered, not cancelled)
        List<OrderModel> orders = orderRepository.findByCompartmentId(compartmentId);
        List<OrderModel> activeOrders = orders.stream()
                .filter(o -> !"delivered".equalsIgnoreCase(o.getStatus()) && !"cancelled".equalsIgnoreCase(o.getStatus()))
                .sorted((a, b) -> {
                    LocalDateTime t1 = a.getCreatedTime() != null ? a.getCreatedTime() : LocalDateTime.MIN;
                    LocalDateTime t2 = b.getCreatedTime() != null ? b.getCreatedTime() : LocalDateTime.MIN;
                    return t2.compareTo(t1); // newest first
                })
                .toList();

        // 3. Customer Goods-out: Only if status is placed or shipping
        Optional<OrderModel> readyOrder = activeOrders.stream()
                .filter(o -> "shipping".equalsIgnoreCase(o.getStatus())
                        || "placed".equalsIgnoreCase(o.getStatus()))
                .findFirst();
        if (readyOrder.isPresent()) {
            OrderModel order = readyOrder.get();
            boolean wasShipping = "shipping".equalsIgnoreCase(order.getStatus());
            if (capacityResourceId != null && !capacityResourceId.isBlank()) {
                order.setCapacityResourceId(capacityResourceId);
            }
            order.setStatus("delivered");
            order.setCompartmentId(null);
            order.setCompletedTime(closedAt != null ? closedAt.toLocalDateTime() : LocalDateTime.now());
            OrderModel saved = orderRepository.save(order);
            publishOrderUpdate(saved);
            log.info("[DoorClosed] Order {} delivered (completed) for compartment {}", order.getOrderCode(), compartmentId);

            if (wasShipping) {
                handlePostDeliveryTaskCleanup(saved);
            }
            return Optional.of(saved);
        }

        // 4. Postman Goods-in: Order is pending (not yet deposited)
        Optional<OrderModel> pendingOrder = activeOrders.stream()
                .filter(o -> "pending".equalsIgnoreCase(o.getStatus()))
                .findFirst();
        if (pendingOrder.isPresent()) {
            OrderModel order = pendingOrder.get();
            if (capacityResourceId != null && !capacityResourceId.isBlank()) {
                order.setCapacityResourceId(capacityResourceId);
            }
            order.setStatus("placed");
            order.setDepositedTime(closedAt != null ? closedAt.toLocalDateTime() : LocalDateTime.now());
            OrderModel saved = orderRepository.save(order);
            publishOrderUpdate(saved);
            if (saved.getEmail() != null && !saved.getEmail().isBlank()) {
                emailService.sendLockerPlacedConfirmation(
                        saved.getEmail(),
                        saved.getFullName(),
                        saved.getOrderCode(),
                        saved.getPinCode(),
                        saved.getCompartmentId(),
                        saved.getAddress()
                );
            }
            log.info("[DoorClosed] Order {} deposited in compartment {}", order.getOrderCode(), compartmentId);
            return Optional.of(saved);
        }

        return Optional.empty();
    }

    private void handlePostDeliveryTaskCleanup(OrderModel completedOrder) {
        if (completedOrder == null) return;
        try {
            List<OrderModel> remainingShipping = getShippingOrders().stream()
                    .filter(o -> !o.getOrderCode().equals(completedOrder.getOrderCode()))
                    .toList();

            if (remainingShipping.isEmpty()) {
                log.info("[DeliveryCleanup] No more shipping orders remaining for robot. Cancelling DCS task {} and returning to charger.",
                        completedOrder.getOrderCode());
                try {
                    robotDispatchService.controlTask(completedOrder.getOrderCode(), "cancel");
                } catch (Exception e) {
                    log.warn("[DeliveryCleanup] Cancel task on DCS failed: {}", e.getMessage());
                }
                try {
                    robotDispatchService.returnToChargingPoint(
                            completedOrder.getCapacityResourceId(),
                            completedOrder.getMapName()
                    );
                } catch (Exception e) {
                    log.warn("[DeliveryCleanup] Return to charger failed: {}", e.getMessage());
                }
            } else {
                log.info("[DeliveryCleanup] Robot still has {} active shipping orders. Route continues.", remainingShipping.size());
            }
        } catch (Exception e) {
            log.warn("[DeliveryCleanup] Error during post delivery task cleanup: {}", e.getMessage());
        }
    }

    public Optional<OrderModel> handleArrived(Integer compartmentId, OffsetDateTime arrivalAt) {
        return handleArrived(compartmentId, null, arrivalAt);
    }

    public Optional<OrderModel> handleArrived(Integer compartmentId, String orderCode, OffsetDateTime arrivalAt) {
        // 1. If explicit orderCode is provided:
        if (orderCode != null && !orderCode.isBlank()) {
            Optional<OrderModel> byCode = orderRepository.findByOrderCode(orderCode.trim());
            if (byCode.isPresent()) {
                OrderModel order = byCode.get();
                if ("delivered".equalsIgnoreCase(order.getStatus())) {
                    log.info("[handleArrived] Order {} arrived at destination, but goods were already taken earlier. Ignoring arrival event.", orderCode);
                    return Optional.of(order);
                }
                if ("shipping".equalsIgnoreCase(order.getStatus())) {
                    order.setArrivalTime(arrivalAt != null ? arrivalAt.toLocalDateTime() : LocalDateTime.now());
                    OrderModel saved = orderRepository.save(order);
                    publishOrderUpdate(saved);
                    log.info("[handleArrived] Order {} marked arrived at destination", orderCode);
                    return Optional.of(saved);
                }
                return Optional.empty();
            }
        }

        // 2. If compartmentId is provided:
        if (compartmentId != null) {
            List<OrderModel> orders = orderRepository.findByCompartmentId(compartmentId);
            Optional<OrderModel> shippingOrder = orders.stream()
                    .filter(o -> !"delivered".equalsIgnoreCase(o.getStatus()) && !"cancelled".equalsIgnoreCase(o.getStatus()))
                    .filter(o -> "shipping".equalsIgnoreCase(o.getStatus()))
                    .findFirst();
            if (shippingOrder.isPresent()) {
                OrderModel order = shippingOrder.get();
                order.setArrivalTime(arrivalAt != null ? arrivalAt.toLocalDateTime() : LocalDateTime.now());
                OrderModel saved = orderRepository.save(order);
                publishOrderUpdate(saved);
                return Optional.of(saved);
            }
            log.info("[handleArrived] Arrived event for compartment {}, but no active shipping order found (already delivered or empty).", compartmentId);
            return Optional.empty();
        }

        // 3. Fallback when neither orderCode nor compartmentId is provided:
        return getShippingOrders().stream()
                .filter(o -> o.getArrivalTime() == null)
                .findFirst()
                .map(order -> {
                    order.setArrivalTime(arrivalAt != null ? arrivalAt.toLocalDateTime() : LocalDateTime.now());
                    OrderModel saved = orderRepository.save(order);
                    publishOrderUpdate(saved);
                    return saved;
                });
    }

    // Verify PIN / OrderCode handler (supports both PIN code and Order code)
    public Optional<VerifyPinResult> verifyPin(String code, String capacityResourceId, String capacityResourceName) {
        if (code == null || code.isBlank()) return Optional.empty();
        String trimmed = code.trim();

        Optional<OrderModel> orderOpt = orderRepository.findByPinCode(trimmed);
        if (orderOpt.isEmpty()) {
            orderOpt = orderRepository.findByOrderCode(trimmed);
        }
        if (orderOpt.isEmpty()) return Optional.empty();
        OrderModel order = orderOpt.get();

        if ("delivered".equalsIgnoreCase(order.getStatus())) {
            log.warn("[VerifyPin] Order {} is already delivered", order.getOrderCode());
            return Optional.of(new VerifyPinResult(order.getOrderCode(), null, "ALREADY_DELIVERED"));
        }
        if ("cancelled".equalsIgnoreCase(order.getStatus())) {
            log.warn("[VerifyPin] Order {} is already cancelled", order.getOrderCode());
            return Optional.of(new VerifyPinResult(order.getOrderCode(), null, "CANCELLED"));
        }

        if (capacityResourceId != null && !capacityResourceId.isBlank()) {
            order.setCapacityResourceId(capacityResourceId);
        }
        if (capacityResourceName != null && !capacityResourceName.isBlank()) {
            order.setCapacityResourceName(capacityResourceName);
        }

        // Determine role based on order status:
        // - pending: Postman is depositing goods → door opens, wait for close → placed
        // - placed / shipping: Customer is picking up → door opens, wait for close → delivered
        String role;
        Integer targetCompartmentId = order.getCompartmentId();

        if ("placed".equalsIgnoreCase(order.getStatus())
                || "shipping".equalsIgnoreCase(order.getStatus())) {
            // Customer pickup: goods already in locker
            role = "CUSTOMER";
            log.info("[VerifyPin] Customer verified order {} in compartment {} (status={})",
                    order.getOrderCode(), targetCompartmentId, order.getStatus());
        } else {
            // Postman deposit: order is pending, assign a FREE compartment if not yet assigned
            role = "POSTMAN";
            targetCompartmentId = allocateFreeCompartment(order.getOrderCode());
            order.setCompartmentId(targetCompartmentId);
            log.info("[VerifyPin] Postman verified order {} → will deposit into compartment {} on door close",
                    order.getOrderCode(), targetCompartmentId);
        }
        orderRepository.save(order);

        // Register so handleDoorClosed knows which order the next door close belongs to
        pendingVerifications.put(order.getOrderCode(), new PendingVerification(
                order.getOrderCode(), role, targetCompartmentId, capacityResourceId, System.currentTimeMillis()));

        return Optional.of(new VerifyPinResult(order.getOrderCode(), targetCompartmentId, role));
    }

    public record VerifyPinResult(String orderCode, Integer compartmentId, String role) {}

    // After DCS accepts sendtask (SUCCESS), move placed → shipping.
    public Optional<OrderModel> markOrderShipping(String orderCode) {
        return orderRepository.findByOrderCode(orderCode)
                .filter(order -> "placed".equalsIgnoreCase(order.getStatus()))
                .map(order -> {
                    order.setStatus("shipping");
                    OrderModel saved = orderRepository.save(order);
                    publishOrderUpdate(saved);
                    return saved;
                });
    }

    // Complete an order
    public void completeOrder(String id) {
        orderRepository.findByOrderCode(id).ifPresent(order -> {
            order.setStatus("delivered");
            order.setCompartmentId(null);
            order.setCompletedTime(LocalDateTime.now());
            orderRepository.save(order);
            publishOrderUpdate(order);
        });
    }

    // Search completed/all orders with filters
    public List<OrderModel> searchHistory(String date, String name, String phone, Integer quantity,
            String orderCode) {
        Query query = new Query();
        query.addCriteria(Criteria.where("status").in("delivered", "cancelled"));

        if (date != null && !date.isEmpty()) {
            LocalDate d = LocalDate.parse(date);
            query.addCriteria(Criteria.where("createdTime").gte(d.atStartOfDay()).lt(d.plusDays(1).atStartOfDay()));
        }
        if (name != null && !name.isEmpty()) {
            query.addCriteria(Criteria.where("fullName").regex(name, "i"));
        }
        if (phone != null && !phone.isEmpty()) {
            query.addCriteria(Criteria.where("phone").regex(phone));
        }
        if (quantity != null) {
            query.addCriteria(Criteria.where("quantity").is(quantity));
        }
        if (orderCode != null && !orderCode.isEmpty()) {
            query.addCriteria(Criteria.where("orderCode").regex(orderCode, "i"));
        }

        return mongoTemplate.find(query, OrderModel.class);
    }

    // Create a new order
    public OrderModel createOrder(OrderModel order) {
        if (order.getStatus() == null || order.getStatus().isEmpty()) {
            order.setStatus("pending");
        }
        if (order.getMapName() == null || order.getMapName().isBlank()) {
            order.setMapName(dcsApiProperties.getMapName());
        } else {
            order.setMapName(order.getMapName().trim());
        }
        order.setOrderCode("SK" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8).toUpperCase());
        order.setPinCode(String.format("%06d", new java.util.Random().nextInt(1000000)));
        OrderModel saved = orderRepository.save(order);
        publishOrderUpdate(saved);
        return saved;
    }

    // Update an existing order
    public OrderModel updateOrder(String id, OrderModel updates) {
        return orderRepository.findByOrderCode(id).map(order -> {
            if (updates.getFullName() != null) order.setFullName(updates.getFullName());
            if (updates.getPhone() != null) order.setPhone(updates.getPhone());
            if (updates.getAddress() != null) order.setAddress(updates.getAddress());
            if (updates.getDestinationName() != null) {
                String d = updates.getDestinationName().trim();
                order.setDestinationName(d.isEmpty() ? null : d);
            }
            if (updates.getMapName() != null) {
                String m = updates.getMapName().trim();
                order.setMapName(m.isEmpty() ? null : m);
            }
            if (updates.getQuantity() != null) order.setQuantity(updates.getQuantity());
            if (updates.getNote() != null) order.setNote(updates.getNote());
            OrderModel saved = orderRepository.save(order);
            publishOrderUpdate(saved);
            return saved;
        }).orElse(null);
    }

    // Cancel an order
    public void cancelOrder(String id) {
        orderRepository.findByOrderCode(id).ifPresent(order -> {
            order.setStatus("cancelled");
            order.setCompartmentId(null);
            orderRepository.save(order);
            publishOrderUpdate(order);
        });
    }

    // Get all orders
    public List<OrderModel> getAllOrders() {
        return orderRepository.findAll();
    }

    public record DcsOrderLocationSyncResult(int pointCount, int ordersUpdated, int ordersUnmatched) {}     // Refresh {@link DcsDestinationRegistry} from DCS points and rewrite each order's
     // {@link OrderModel#getDestinationName()} + {@link OrderModel#getAddress()} when a POI matches.
     // When {@code mapName} is set, matching orders also get {@link OrderModel#setMapName(String)}.    public DcsOrderLocationSyncResult syncOrderDestinationsFromDcsPoints(String mapName, List<DcsCampusPoint> points) {
        if (points == null || points.isEmpty()) {
            return new DcsOrderLocationSyncResult(0, 0, 0);
        }
        dcsDestinationRegistry.applyFromRemotePoints(points);
        String mapStamp = (mapName != null && !mapName.isBlank()) ? mapName.trim() : null;
        int updated = 0;
        int unmatched = 0;
        for (OrderModel o : orderRepository.findAll()) {
            Optional<DcsCampusPoint> match = matchOrderToPoint(o, points);
            if (match.isEmpty()) {
                unmatched++;
                continue;
            }
            DcsCampusPoint p = match.get();
            boolean changed = false;
            if (!Objects.equals(o.getDestinationName(), p.name()) || !Objects.equals(o.getAddress(), p.address())) {
                o.setDestinationName(p.name());
                o.setAddress(p.address());
                changed = true;
            }
            if (mapStamp != null && !Objects.equals(o.getMapName(), mapStamp)) {
                o.setMapName(mapStamp);
                changed = true;
            }
            if (changed) {
                orderRepository.save(o);
                updated++;
            }
        }
        publishActiveOrders();
        return new DcsOrderLocationSyncResult(points.size(), updated, unmatched);
    }

    private static Optional<DcsCampusPoint> matchOrderToPoint(OrderModel o, List<DcsCampusPoint> points) {
        String od = o.getDestinationName() != null ? DcsDestinationRegistry.nfc(o.getDestinationName()) : "";
        String oa = o.getAddress() != null ? DcsDestinationRegistry.nfc(o.getAddress()) : "";

        if (!od.isEmpty()) {
            for (DcsCampusPoint p : points) {
                if (DcsDestinationRegistry.nfc(p.name()).equals(od)) {
                    return Optional.of(p);
                }
            }
        }
        for (DcsCampusPoint p : points) {
            if (!oa.isEmpty() && DcsDestinationRegistry.nfc(p.address()).equals(oa)) {
                return Optional.of(p);
            }
        }
        for (DcsCampusPoint p : points) {
            String pa = DcsDestinationRegistry.nfc(p.address());
            if (pa.length() >= 8 && !oa.isEmpty() && (oa.contains(pa) || pa.contains(oa))) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    private void publishActiveOrders() {
        try {
            List<OrderModel> active = getActiveOrders();
            messagingTemplate.convertAndSend("/topic/orders", active);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void publishOrderUpdate(OrderModel order) {
        publishActiveOrders();
        if (order != null && order.getOrderCode() != null && !order.getOrderCode().isBlank()) {
            try {
                messagingTemplate.convertAndSend("/topic/orders/" + order.getOrderCode().trim(), order);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

