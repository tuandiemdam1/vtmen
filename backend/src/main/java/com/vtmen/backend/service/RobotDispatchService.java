package com.vtmen.backend.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtmen.backend.config.RobotTaskProperties;
import com.vtmen.backend.model.OrderModel;
import com.vtmen.backend.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Service
public class RobotDispatchService {

    // Explicit UTF-8 so DCS receives correct Vietnamese in destination.name (avoid server defaulting to Latin-1).
    private static final MediaType APPLICATION_JSON_UTF8 =
            new MediaType("application", "json", StandardCharsets.UTF_8);

    @Autowired
    private RestClient robotRestClient;

    @Autowired
    private RobotTaskProperties robotTaskProperties;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private DcsDestinationRegistry dcsDestinationRegistry;
     // POST sendtask to DCS; on SUCCESS marks order {@code shipping}.
     // <p>
     // The JSON sent to DCS is built from the saved order: {@code order_id} = {@link OrderModel#getOrderCode()},
     // {@code compartment_id} from the order, {@code destination.address_text} from {@link OrderModel#getAddress()},
     // {@code destination.name} from {@link OrderModel#getDestinationName()} if set, else config default.
     // Optional {@link DispatchRobotRequest} only overrides {@code robot_id} and destination fields when provided.
    public DispatchRobotResult dispatchPlacedOrder(String orderCode, DispatchRobotRequest request) {
        DispatchRobotRequest req = request != null ? request : new DispatchRobotRequest(null, null);

        OrderModel order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        if (!"placed".equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Order must be placed to dispatch robot (current: " + order.getStatus() + ")");
        }
        if (order.getCompartmentId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "compartment_id is required — assign via POST /api/dcs/deposit-closed first");
        }

        String capacityResourceId = (req.robotId() != null && !req.robotId().isBlank())
                ? req.robotId().trim()
                : (order.getCapacityResourceId() != null && !order.getCapacityResourceId().isBlank()
                        ? order.getCapacityResourceId()
                        : (order.getRobotId() != null && !order.getRobotId().isBlank() 
                                ? order.getRobotId() 
                                : robotTaskProperties.getDefaultRobotId()));

        String capacityResourceName = (order.getCapacityResourceName() != null && !order.getCapacityResourceName().isBlank())
                ? order.getCapacityResourceName()
                : robotTaskProperties.getDefaultCapacityResourceName();

        CreateSubTaskVo subTask = buildSubTask(order, req);

        String siteId = (order.getMapName() != null && !order.getMapName().isBlank())
                ? order.getMapName().trim()
                : robotTaskProperties.getDefaultSiteId();

        CreateTaskAndBeginPayload body = new CreateTaskAndBeginPayload(
                orderCode,
                capacityResourceId,
                capacityResourceName,
                siteId,
                java.util.List.of(subTask)
        );

        SendTaskVtMenResponse robotBody;
        String rawBody = null;
        try {
            ResponseEntity<String> entity = robotRestClient.post()
                    .uri(robotTaskProperties.getDispatchUrl())
                    .contentType(APPLICATION_JSON_UTF8)
                    .accept(APPLICATION_JSON_UTF8)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);

            if (!entity.getStatusCode().is2xxSuccessful()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Robot API returned HTTP " + entity.getStatusCode().value());
            }
            rawBody = entity.getBody();
            ObjectMapper mapper = new ObjectMapper();
            robotBody = mapper.readValue(rawBody, SendTaskVtMenResponse.class);
        } catch (RestClientResponseException e) {
            String hint = e.getResponseBodyAsString();
            if (hint != null && hint.length() > 200) {
                hint = hint.substring(0, 200) + "...";
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Robot API error: HTTP " + e.getStatusCode().value()
                            + (hint != null && !hint.isBlank() ? " — " + hint : ""));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Robot API unreachable or parsing error: " + e.getMessage() + (rawBody != null ? " Raw: " + rawBody : ""));
        }

        boolean isSuccess = false;
        if (robotBody != null) {
            if ("SUCCESS".equalsIgnoreCase(robotBody.status())) isSuccess = true;
            if ("true".equalsIgnoreCase(robotBody.status())) isSuccess = true;
        }

        if (robotBody == null || !isSuccess) {
            String msg = robotBody != null ? robotBody.msg() : null;
            if (msg == null && robotBody != null) msg = robotBody.message();
            if (msg == null) msg = "Robot did not return SUCCESS";
            throw new ResponseStatusException(HttpStatus.CONFLICT, msg + " | Raw Response: " + rawBody);
        }

        OrderModel updated = orderService.markOrderShipping(orderCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Could not move order to shipping"));

        return new DispatchRobotResult(
                updated,
                robotBody.status(),
                robotBody.message(),
                robotBody.estimatedTimeOfArrival()
        );
    }

    private CreateSubTaskVo buildSubTask(OrderModel order, DispatchRobotRequest req) {
        String fallbackName = robotTaskProperties.getDefaultDestinationName();
        String name = fallbackName;
        if (order.getDestinationName() != null && !order.getDestinationName().isBlank()) {
            name = order.getDestinationName().trim();
        }

        Optional<DcsDestinationRegistry.Result> canon = dcsDestinationRegistry.resolve(
                order.getDestinationName(), fallbackName);
        if (canon.isPresent()) {
            name = canon.get().name();
        }

        if (req.destination() != null) {
            if (req.destination().name() != null && !req.destination().name().isBlank()) {
                name = req.destination().name().trim();
            }
        }

        return new CreateSubTaskVo(
                DcsDestinationRegistry.nfc(name), // Assuming name is ID for now
                DcsDestinationRegistry.nfc(name),
                0);
    }

    public void openCompartment(String robotId, int compartmentId) {
        String rId = (robotId != null && !robotId.isBlank()) ? robotId.trim() : robotTaskProperties.getDefaultRobotId();
        CabinetMissionOpenPayload body = new CabinetMissionOpenPayload(rId, compartmentId);

        try {
            ResponseEntity<String> entity = robotRestClient.post()
                    .uri(robotTaskProperties.getDoorControlUrl())
                    .contentType(APPLICATION_JSON_UTF8)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);

            if (!entity.getStatusCode().is2xxSuccessful()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Robot Open Door API returned HTTP " + entity.getStatusCode().value());
            }
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Robot Open Door API unreachable: " + e.getMessage());
        }
    }

    public void returnToChargingPoint(String robotId, String siteId) {
        String rId = (robotId != null && !robotId.isBlank()) ? robotId.trim() : robotTaskProperties.getDefaultRobotId();
        String sId = (siteId != null && !siteId.isBlank()) ? siteId.trim() : robotTaskProperties.getDefaultSiteId();
        
        // Use a mock charging point ID as requested
        String mockChargingPoint = "mock_charging_point_1";
        CreateSubTaskVo subTask = new CreateSubTaskVo(
                mockChargingPoint,
                "Điểm Sạc Giả Lập",
                0
        );

        CreateTaskAndBeginPayload body = new CreateTaskAndBeginPayload(
                "ReturnToCharging_" + System.currentTimeMillis(),
                rId,
                robotTaskProperties.getDefaultCapacityResourceName(),
                sId,
                java.util.List.of(subTask)
        );

        try {
            ResponseEntity<SendTaskVtMenResponse> entity = robotRestClient.post()
                    .uri(robotTaskProperties.getDispatchUrl())
                    .contentType(APPLICATION_JSON_UTF8)
                    .accept(APPLICATION_JSON_UTF8)
                    .body(body)
                    .retrieve()
                    .toEntity(SendTaskVtMenResponse.class);

            if (!entity.getStatusCode().is2xxSuccessful()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Robot Return To Charge API returned HTTP " + entity.getStatusCode().value());
            }
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Robot Return To Charge API unreachable: " + e.getMessage());
        }
    }

    public void controlTask(String taskId, String action) {
        String endpoint;
        switch (action.toLowerCase()) {
            case "pause":
                endpoint = "/task/interface/pauseTask";
                break;
            case "recover":
                endpoint = "/task/interface/recoverTask";
                break;
            case "cancel":
                endpoint = "/task/interface/cancelTask";
                break;
            default:
                throw new IllegalArgumentException("Unknown action: " + action);
        }

        // Extract base URL from dispatchUrl dynamically
        String fullDispatchUrl = robotTaskProperties.getDispatchUrl();
        String baseUrl = fullDispatchUrl;
        int pathIndex = fullDispatchUrl.indexOf("/", fullDispatchUrl.indexOf("://") + 3);
        if (pathIndex > 0) {
            baseUrl = fullDispatchUrl.substring(0, pathIndex);
        }
        
        try {
            ResponseEntity<String> entity = robotRestClient.get()
                    .uri(baseUrl + endpoint + "?taskId={taskId}", taskId)
                    .retrieve()
                    .toEntity(String.class);

            if (!entity.getStatusCode().is2xxSuccessful()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Robot " + action + " API returned HTTP " + entity.getStatusCode().value());
            }
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Robot " + action + " API unreachable: " + e.getMessage());
        }
    }

    // --- Request to vtmen backend (optional body from dashboard) ---

    public record DispatchRobotRequest(
            @JsonProperty("robot_id") String robotId,
            @JsonProperty("destination") DestinationOverride destination
    ) {
        public record DestinationOverride(
                String name,
                @JsonProperty("address_text") String addressText
        ) {}
    }

    public record DispatchRobotResult(
            OrderModel order,
            @JsonProperty("robot_status") String robotStatus,
            String message,
            @JsonProperty("estimated_time_of_arrival") Integer estimatedTimeOfArrival
    ) {}

    // --- Payload / response for external DCS ---

    // --- Payload / response for external DCS ---

    private record CreateSubTaskVo(
            String parkPointId,
            String parkPointName,
            int orderBy
    ) {}

    private record CreateTaskAndBeginPayload(
            String taskName,
            String capacityResourceId,
            String capacityResourceName,
            String siteId,
            java.util.List<CreateSubTaskVo> createSubTaskVoList
    ) {}

    private record CabinetMissionOpenPayload(
            String capacityResourceId,
            int boxIndex
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SendTaskVtMenResponse(
            String status,
            String message,
            String msg,
            Integer code,
            @JsonProperty("estimated_time_of_arrival") Integer estimatedTimeOfArrival
    ) {}
}
