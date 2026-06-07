package com.vtmen.backend.service;

import com.vtmen.backend.config.DcsApiProperties;
import com.vtmen.backend.model.OrderModel;
import com.vtmen.backend.repository.OrderRepository;
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
                    OrderModel saved = orderRepository.save(order);
                    publishActiveOrders();
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
                    if (order.getStatus() == null || "pending".equalsIgnoreCase(order.getStatus())) {
                        order.setStatus("placed");
                    }
                    OrderModel saved = orderRepository.save(order);
                    publishActiveOrders();
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
            if (closedAt != null) {
                order.setDepositedTime(closedAt.toLocalDateTime());
            } else {
                order.setDepositedTime(LocalDateTime.now());
            }
            orderRepository.save(order);
            publishActiveOrders();
            return Optional.of("Placed successfully");
        }

        order.setCompartmentId(null);
        order.setDepositedTime(null);
        orderRepository.save(order);
        publishActiveOrders();
        return Optional.of("Compartment removed");
    }

    // --- New Webhook handlers ---
    public Optional<OrderModel> handleDoorClosed(Integer compartmentId, String capacityResourceId, OffsetDateTime closedAt) {
        if (compartmentId == null) return Optional.empty();
        List<OrderModel> orders = orderRepository.findByCompartmentId(compartmentId);

        // Goods-in: order is placed
        Optional<OrderModel> placedOrder = orders.stream().filter(o -> "placed".equalsIgnoreCase(o.getStatus())).findFirst();
        if (placedOrder.isPresent()) {
            OrderModel order = placedOrder.get();
            if (capacityResourceId != null && !capacityResourceId.isBlank()) order.setCapacityResourceId(capacityResourceId);
            order.setDepositedTime(closedAt != null ? closedAt.toLocalDateTime() : LocalDateTime.now());
            OrderModel saved = orderRepository.save(order);
            publishActiveOrders();
            return Optional.of(saved);
        }

        // Goods-out: order is shipping
        Optional<OrderModel> shippingOrder = orders.stream().filter(o -> "shipping".equalsIgnoreCase(o.getStatus())).findFirst();
        if (shippingOrder.isPresent()) {
            OrderModel order = shippingOrder.get();
            if (capacityResourceId != null && !capacityResourceId.isBlank()) order.setCapacityResourceId(capacityResourceId);
            order.setStatus("delivered");
            order.setCompartmentId(null);
            order.setCompletedTime(closedAt != null ? closedAt.toLocalDateTime() : LocalDateTime.now());
            OrderModel saved = orderRepository.save(order);
            publishActiveOrders();
            return Optional.of(saved);
        }
        return Optional.empty();
    }

    public Optional<OrderModel> handleArrived(Integer compartmentId, OffsetDateTime arrivalAt) {
        if (compartmentId == null) {
             // If compartmentId is null, we just find any shipping order without arrivalTime
             return getShippingOrders().stream()
                     .filter(o -> o.getArrivalTime() == null)
                     .findFirst()
                     .map(order -> {
                         order.setArrivalTime(arrivalAt != null ? arrivalAt.toLocalDateTime() : LocalDateTime.now());
                         OrderModel saved = orderRepository.save(order);
                         publishActiveOrders();
                         return saved;
                     });
        }

        List<OrderModel> orders = orderRepository.findByCompartmentId(compartmentId);
        Optional<OrderModel> shippingOrder = orders.stream().filter(o -> "shipping".equalsIgnoreCase(o.getStatus())).findFirst();
        if (shippingOrder.isPresent()) {
            OrderModel order = shippingOrder.get();
            order.setArrivalTime(arrivalAt != null ? arrivalAt.toLocalDateTime() : LocalDateTime.now());
            OrderModel saved = orderRepository.save(order);
            publishActiveOrders();
            return Optional.of(saved);
        }
        return Optional.empty();
    }

    // Verify PIN handler
    public Optional<VerifyPinResult> verifyPin(String pinCode, String capacityResourceId, String capacityResourceName) {
        Optional<OrderModel> orderOpt = orderRepository.findByPinCode(pinCode);
        if (orderOpt.isEmpty()) return Optional.empty();
        OrderModel order = orderOpt.get();

        if (capacityResourceId != null && !capacityResourceId.isBlank()) {
            order.setCapacityResourceId(capacityResourceId);
        }
        if (capacityResourceName != null && !capacityResourceName.isBlank()) {
            order.setCapacityResourceName(capacityResourceName);
        }

        if ("pending".equalsIgnoreCase(order.getStatus()) || "placed".equalsIgnoreCase(order.getStatus())) {
            if ("pending".equalsIgnoreCase(order.getStatus())) {
                order.setStatus("placed");
            }
            if (order.getCompartmentId() == null) {
                // Assign a compartment (dummy logic, pick 1)
                order.setCompartmentId(1);
            }
            orderRepository.save(order);
            publishActiveOrders();
            return Optional.of(new VerifyPinResult(order.getOrderCode(), order.getCompartmentId(), "POSTMAN"));
        } else if ("shipping".equalsIgnoreCase(order.getStatus())) {
            return Optional.of(new VerifyPinResult(order.getOrderCode(), order.getCompartmentId(), "CUSTOMER"));
        }
        return Optional.empty();
    }

    public record VerifyPinResult(String orderCode, Integer compartmentId, String role) {}

    // After DCS accepts sendtask (SUCCESS), move placed → shipping.
    public Optional<OrderModel> markOrderShipping(String orderCode) {
        return orderRepository.findByOrderCode(orderCode)
                .filter(order -> "placed".equalsIgnoreCase(order.getStatus()))
                .map(order -> {
                    order.setStatus("shipping");
                    OrderModel saved = orderRepository.save(order);
                    publishActiveOrders();
                    return saved;
                });
    }

    // Complete an order
    public void completeOrder(String id) {
        orderRepository.findByOrderCode(id).ifPresent(order -> {
            order.setStatus("delivered");
            order.setCompletedTime(LocalDateTime.now());
            orderRepository.save(order);
            publishActiveOrders();
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
        publishActiveOrders();
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
            publishActiveOrders();
            return saved;
        }).orElse(null);
    }

    // Cancel an order
    public void cancelOrder(String id) {
        orderRepository.findByOrderCode(id).ifPresent(order -> {
            order.setStatus("cancelled");
            orderRepository.save(order);
        });
        publishActiveOrders();
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
}

