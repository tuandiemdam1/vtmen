package com.vtmen.backend.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vtmen.backend.config.DcsApiProperties;
import com.vtmen.backend.model.OrderModel;
import com.vtmen.backend.service.CampusMapService;
import com.vtmen.backend.service.DcsRemoteLocationsClient;
import com.vtmen.backend.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Optional;
import com.vtmen.backend.service.RobotDispatchService;

@RestController
@RequestMapping("/api/v1/dcs")
public class DcsController {

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
        if (request == null || request.pinCode() == null || request.pinCode().isBlank()) {
            return ResponseEntity.badRequest().body(new VerifyPinResponse(400, "Missing pinCode", new VerifyPinData(false, null, null, null)));
        }

        Optional<OrderService.VerifyPinResult> result = orderService.verifyPin(
            request.pinCode(), request.capacityResourceId(), request.capacityResourceName()
        );
        if (result.isPresent()) {
            OrderService.VerifyPinResult data = result.get();
            try {
                // Call DCS to open door
                robotDispatchService.openCompartment(request.capacityResourceId(), data.compartmentId());
            } catch (Exception e) {
                // Ignore open door failure for now, or log it
            }
            return ResponseEntity.ok(new VerifyPinResponse(200, "PIN hợp lệ",
                    new VerifyPinData(true, data.orderCode(), data.compartmentId(), data.role())));
        } else {
            return ResponseEntity.ok(new VerifyPinResponse(400, "Mã PIN không hợp lệ",
                    new VerifyPinData(false, null, null, null)));
        }
    }

    @PostMapping("/webhook/event")
    public ResponseEntity<SimpleResponse> webhookEvent(@RequestBody WebhookEventRequest request) {
        if (request == null || request.eventType() == null) {
            return ResponseEntity.badRequest().body(new SimpleResponse(400, "Missing eventType"));
        }

        switch (request.eventType()) {
            case "DOOR_CLOSED":
                orderService.handleDoorClosed(request.compartmentId(), request.capacityResourceId(), request.timestamp());
                break;
            case "ARRIVED_DESTINATION":
                orderService.handleArrived(request.compartmentId(), request.timestamp());
                break;
            case "ERROR":
                // Handle error if needed
                break;
            default:
                break;
        }

        return ResponseEntity.ok(new SimpleResponse(200, "Event received successfully"));
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

    public record VerifyPinRequest(
            String pinCode,
            String capacityResourceId,
            String capacityResourceName,
            OffsetDateTime timestamp
    ) {}

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

    public record WebhookEventRequest(
            String capacityResourceId,
            String capacityResourceName,
            String eventType,
            Integer compartmentId,
            Integer batteryLevel,
            Object location,
            OffsetDateTime timestamp
    ) {}

    public record SimpleResponse(int code, String message) {}
}

