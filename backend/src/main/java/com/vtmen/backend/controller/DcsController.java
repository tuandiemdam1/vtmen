package com.vtmen.backend.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.vtmen.backend.config.DcsApiProperties;
import com.vtmen.backend.service.CampusMapService;
import com.vtmen.backend.service.DcsRemoteLocationsClient;
import com.vtmen.backend.service.OrderService;
import com.vtmen.backend.service.RobotDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Optional;

@RestController
@RequestMapping({"/api/v1/dcs", "/api/dcs"})
public class DcsController {

    private static final Logger log = LoggerFactory.getLogger(DcsController.class);

    @Autowired
    private OrderService orderService;

    @Autowired
    private DcsRemoteLocationsClient dcsRemoteLocationsClient;

    @Autowired
    private CampusMapService campusMapService;

    @Autowired
    private DcsApiProperties dcsApiProperties;

    @Autowired
    private RobotDispatchService robotDispatchService;

    @PostMapping("/auth/verify-pin")
    public ResponseEntity<VerifyPinResponse> verifyPin(@RequestBody VerifyPinRequest request) {
        String code = request != null ? request.getEffectiveCode() : null;
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(new VerifyPinResponse(400, "Missing pinCode or orderCode", new VerifyPinData(false, null, null, null)));
        }

        String resourceId = request.getEffectiveResourceId();
        String resourceName = request.getEffectiveResourceName();

        Optional<OrderService.VerifyPinResult> result = orderService.verifyPin(code, resourceId, resourceName);
        if (result.isPresent()) {
            OrderService.VerifyPinResult data = result.get();
            if ("ALREADY_DELIVERED".equalsIgnoreCase(data.role())) {
                return ResponseEntity.ok(new VerifyPinResponse(400, "Đơn hàng này đã được lấy ra khỏi tủ trước đó",
                        new VerifyPinData(false, data.orderCode(), null, data.role())));
            }
            if ("CANCELLED".equalsIgnoreCase(data.role())) {
                return ResponseEntity.ok(new VerifyPinResponse(400, "Đơn hàng này đã bị hủy",
                        new VerifyPinData(false, data.orderCode(), null, data.role())));
            }

            try {
                // Call DCS to open door
                robotDispatchService.openCompartment(resourceId, data.compartmentId());
                log.info("[DcsAuth] Opened compartment {} for order {} ({})", data.compartmentId(), data.orderCode(), data.role());
            } catch (Exception e) {
                log.warn("[DcsAuth] Open door failed: {}", e.getMessage());
            }
            return ResponseEntity.ok(new VerifyPinResponse(200, "Xác thực thành công",
                    new VerifyPinData(true, data.orderCode(), data.compartmentId(), data.role())));
        } else {
            return ResponseEntity.ok(new VerifyPinResponse(400, "Mã PIN hoặc mã đơn hàng không hợp lệ",
                    new VerifyPinData(false, null, null, null)));
        }
    }

    @PostMapping({"/webhook/event", "/door-closed"})
    public ResponseEntity<SimpleResponse> webhookEvent(@RequestBody WebhookEventRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(new SimpleResponse(400, "Missing body"));
        }

        String eventType = request.getEffectiveEventType();
        Integer compartmentId = request.getEffectiveCompartmentId();
        String orderCode = request.getEffectiveOrderCode();
        String resourceId = request.getEffectiveResourceId();
        OffsetDateTime timestamp = request.timestamp();

        log.info("[DcsWebhook] Received event: type={}, compartment={}, orderCode={}, resourceId={}",
                eventType, compartmentId, orderCode, resourceId);

        if ("DOOR_CLOSED".equalsIgnoreCase(eventType) || eventType == null) {
            // Default to door-closed handling if eventType is omitted on /door-closed endpoint
            orderService.handleDoorClosed(compartmentId, orderCode, resourceId, timestamp);
        } else if ("ARRIVED_DESTINATION".equalsIgnoreCase(eventType) || "ARRIVED".equalsIgnoreCase(eventType)) {
            orderService.handleArrived(compartmentId, orderCode, timestamp);
        } else if ("ERROR".equalsIgnoreCase(eventType)) {
            log.warn("[DcsWebhook] DCS reported error event for compartment {}", compartmentId);
        } else {
            log.info("[DcsWebhook] Unhandled eventType: {}", eventType);
        }

        return ResponseEntity.ok(new SimpleResponse(200, "Event processed successfully"));
    }

    @PostMapping("/deposit-closed")
    public ResponseEntity<SimpleResponse> depositClosed(@RequestBody DepositClosedRequest request) {
        if (request == null || request.getEffectiveOrderCode() == null) {
            return ResponseEntity.badRequest().body(new SimpleResponse(400, "Missing orderCode"));
        }
        String orderCode = request.getEffectiveOrderCode();
        Integer compartmentId = request.getEffectiveCompartmentId();
        OffsetDateTime closedAt = request.timestamp();

        Optional<String> res = orderService.applyDepositClosed(orderCode, compartmentId, closedAt);
        return res.map(msg -> ResponseEntity.ok(new SimpleResponse(200, msg)))
                .orElseGet(() -> ResponseEntity.badRequest().body(new SimpleResponse(404, "Order not found or ineligible")));
    }

    @PostMapping("/sync-order-locations-from-dcs")
    public ResponseEntity<OrderLocationSyncResponse> syncOrderLocationsFromDcs(
            @RequestBody(required = false) SyncOrderLocationsRequest body
    ) {
        try {
            String mapOverride = body != null ? body.mapName() : null;
            String mapKey = (mapOverride != null && !mapOverride.isBlank())
                    ? mapOverride.trim()
                    : dcsApiProperties.getMapName();
            var points = dcsRemoteLocationsClient.fetchCampusPoints(mapKey);
            if (points.isEmpty()) {
                return ResponseEntity.badRequest().body(
                        new OrderLocationSyncResponse(0, 0, 0, "DCS returned no points (check map_name / network)"));
            }
            campusMapService.saveOrReplaceFromDcsPoints(mapKey, points);

            if (!mapKey.equals(dcsApiProperties.getMapName())) {
                return ResponseEntity.ok(new OrderLocationSyncResponse(
                        points.size(),
                        0,
                        0,
                        "Saved map to Mongo; registry/order sync skipped (not default vtmen.dcs.map-name)"));
            }

            OrderService.DcsOrderLocationSyncResult r =
                    orderService.syncOrderDestinationsFromDcsPoints(mapKey, points);
            return ResponseEntity.ok(new OrderLocationSyncResponse(
                    r.pointCount(),
                    r.ordersUpdated(),
                    r.ordersUnmatched(),
                    "OK"));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(
                    new OrderLocationSyncResponse(0, 0, 0, ex.getMessage() != null ? ex.getMessage() : "sync failed"));
        }
    }

    public record SyncOrderLocationsRequest(@JsonProperty("map_name") String mapName) {}

    public record OrderLocationSyncResponse(
            int pointCount,
            int ordersUpdated,
            int ordersUnmatched,
            String message
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VerifyPinRequest(
            @JsonProperty("pinCode") String pinCode,
            @JsonProperty("pin_code") String pinCodeSnake,
            @JsonProperty("orderCode") String orderCode,
            @JsonProperty("order_code") String orderCodeSnake,
            @JsonProperty("code") String code,
            @JsonProperty("capacityResourceId") String capacityResourceId,
            @JsonProperty("capacity_resource_id") String capacityResourceIdSnake,
            @JsonProperty("capacityResourceName") String capacityResourceName,
            @JsonProperty("capacity_resource_name") String capacityResourceNameSnake,
            @JsonProperty("timestamp") OffsetDateTime timestamp
    ) {
        public String getEffectiveCode() {
            if (pinCode != null && !pinCode.isBlank()) return pinCode.trim();
            if (pinCodeSnake != null && !pinCodeSnake.isBlank()) return pinCodeSnake.trim();
            if (orderCode != null && !orderCode.isBlank()) return orderCode.trim();
            if (orderCodeSnake != null && !orderCodeSnake.isBlank()) return orderCodeSnake.trim();
            if (code != null && !code.isBlank()) return code.trim();
            return null;
        }

        public String getEffectiveResourceId() {
            if (capacityResourceId != null && !capacityResourceId.isBlank()) return capacityResourceId.trim();
            if (capacityResourceIdSnake != null && !capacityResourceIdSnake.isBlank()) return capacityResourceIdSnake.trim();
            return null;
        }

        public String getEffectiveResourceName() {
            if (capacityResourceName != null && !capacityResourceName.isBlank()) return capacityResourceName.trim();
            if (capacityResourceNameSnake != null && !capacityResourceNameSnake.isBlank()) return capacityResourceNameSnake.trim();
            return null;
        }
    }

    public record VerifyPinData(
            boolean isValid,
            String orderCode,
            Integer compartmentId,
            String role
    ) {}

    public record VerifyPinResponse(
            int code,
            String message,
            VerifyPinData data
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebhookEventRequest(
            @JsonProperty("capacity_resource_id") String capacityResourceIdSnake,
            @JsonProperty("capacityResourceId") String capacityResourceId,
            @JsonProperty("capacity_resource_name") String capacityResourceNameSnake,
            @JsonProperty("capacityResourceName") String capacityResourceName,
            @JsonProperty("event_type") String eventTypeSnake,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("compartment_id") Integer compartmentIdSnake,
            @JsonProperty("compartmentId") Integer compartmentId,
            @JsonProperty("box_index") Integer boxIndexSnake,
            @JsonProperty("boxIndex") Integer boxIndex,
            @JsonProperty("order_code") String orderCodeSnake,
            @JsonProperty("orderCode") String orderCode,
            @JsonProperty("battery_level") Integer batteryLevel,
            @JsonProperty("location") Object location,
            @JsonProperty("timestamp") OffsetDateTime timestamp
    ) {
        public String getEffectiveEventType() {
            if (eventType != null && !eventType.isBlank()) return eventType.trim().toUpperCase();
            if (eventTypeSnake != null && !eventTypeSnake.isBlank()) return eventTypeSnake.trim().toUpperCase();
            return null;
        }

        public Integer getEffectiveCompartmentId() {
            if (compartmentId != null) return compartmentId;
            if (compartmentIdSnake != null) return compartmentIdSnake;
            if (boxIndex != null) return boxIndex;
            if (boxIndexSnake != null) return boxIndexSnake;
            return null;
        }

        public String getEffectiveOrderCode() {
            if (orderCode != null && !orderCode.isBlank()) return orderCode.trim();
            if (orderCodeSnake != null && !orderCodeSnake.isBlank()) return orderCodeSnake.trim();
            return null;
        }

        public String getEffectiveResourceId() {
            if (capacityResourceId != null && !capacityResourceId.isBlank()) return capacityResourceId.trim();
            if (capacityResourceIdSnake != null && !capacityResourceIdSnake.isBlank()) return capacityResourceIdSnake.trim();
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DepositClosedRequest(
            @JsonProperty("orderCode") String orderCode,
            @JsonProperty("order_code") String orderCodeSnake,
            @JsonProperty("compartmentId") Integer compartmentId,
            @JsonProperty("compartment_id") Integer compartmentIdSnake,
            @JsonProperty("boxIndex") Integer boxIndex,
            @JsonProperty("box_index") Integer boxIndexSnake,
            @JsonProperty("timestamp") OffsetDateTime timestamp
    ) {
        public String getEffectiveOrderCode() {
            if (orderCode != null && !orderCode.isBlank()) return orderCode.trim();
            if (orderCodeSnake != null && !orderCodeSnake.isBlank()) return orderCodeSnake.trim();
            return null;
        }

        public Integer getEffectiveCompartmentId() {
            if (compartmentId != null) return compartmentId;
            if (compartmentIdSnake != null) return compartmentIdSnake;
            if (boxIndex != null) return boxIndex;
            if (boxIndexSnake != null) return boxIndexSnake;
            return null;
        }
    }

    public record SimpleResponse(int code, String message) {}
}

