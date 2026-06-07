package com.vtmen.backend.controller;

import com.vtmen.backend.model.OrderModel;
import com.vtmen.backend.service.OrderService;
import com.vtmen.backend.service.RobotDispatchService;
import com.vtmen.backend.service.RobotDispatchService.DispatchRobotRequest;
import com.vtmen.backend.service.RobotDispatchService.DispatchRobotResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private RobotDispatchService robotDispatchService;

    // GET /api/orders/active — Active orders (non-delivered, non-cancelled)
    @GetMapping("/active")
    public List<OrderModel> getActiveOrders() {
        return orderService.getActiveOrders();
    }

    // GET /api/orders/completed — Completed orders
    @GetMapping("/completed")
    public List<OrderModel> getCompletedOrders() {
        return orderService.getCompletedOrders();
    }

    // GET /api/orders/cancelled — Cancelled orders
    @GetMapping("/cancelled")
    public List<OrderModel> getCancelledOrders() {
        return orderService.getCancelledOrders();
    }

    // GET /api/orders/shipping — Shipping orders
    @GetMapping("/shipping")
    public List<OrderModel> getShippingOrders() {
        return orderService.getShippingOrders();
    }

    // GET /api/orders/placed — Placed orders
    @GetMapping("/placed")
    public List<OrderModel> getPlacedOrders() {
        return orderService.getPlacedOrders();
    }

    // GET /api/orders/pending — Pending orders
    @GetMapping("/pending")
    public List<OrderModel> getPendingOrders() {
        return orderService.getPendingOrders();
    }

    // GET /api/orders/pending/{orderCode} — Get one pending order by orderCode
    @GetMapping("/pending/{orderCode}")
    public ResponseEntity<OrderModel> getPendingOrderByOrderCode(@PathVariable String orderCode) {
        return orderService.getPendingOrderByOrderCode(orderCode)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // GET /api/orders/{orderCode} — Get one order by orderCode
    @GetMapping("/{orderCode}")
    public ResponseEntity<OrderModel> getOrderByOrderCode(@PathVariable String orderCode) {
        return orderService.getOrderByOrderCode(orderCode)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // GET /api/orders/history — Search delivered orders
    @GetMapping("/history")
    public List<OrderModel> searchHistory(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) Integer quantity,
            @RequestParam(required = false) String orderCode) {
        return orderService.searchHistory(date, name, phone, quantity, orderCode);
    }

    // GET /api/orders — All orders
    @GetMapping
    public List<OrderModel> getAllOrders() {
        return orderService.getAllOrders();
    }

    // POST /api/orders/create — Create a new order
    @PostMapping("/create")
    public OrderModel createOrder(@RequestBody OrderModel order) {
        return orderService.createOrder(order);
    }

    // PATCH /api/orders/{orderCode}/status — Update status (pending -> placed)
    @PatchMapping("/{orderCode}/status")
    public ResponseEntity<OrderModel> updateOrderStatus(
            @PathVariable String orderCode,
            @RequestBody StatusUpdateRequest body
    ) {
        if (body == null || body.status() == null || !"placed".equalsIgnoreCase(body.status())) {
            return ResponseEntity.badRequest().build();
        }

        return orderService.updateOrderStatusPendingToPlaced(orderCode, body.compartmentId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // POST /api/orders/{orderCode}/dispatch-robot — Call DCS sendtask; on SUCCESS → shipping
    @PostMapping("/{orderCode}/dispatch-robot")
    public ResponseEntity<DispatchRobotResult> dispatchRobot(
            @PathVariable String orderCode,
            @RequestBody(required = false) DispatchRobotRequest body
    ) {
        return ResponseEntity.ok(robotDispatchService.dispatchPlacedOrder(orderCode, body));
    }

    // POST /api/orders/{id}/complete — Mark order as delivered
    @PostMapping("/{id}/complete")
    public void completeOrder(@PathVariable String id) {
        orderService.completeOrder(id);
    }

    // PUT /api/orders/{id} — Update an existing order
    @PutMapping("/{id}")
    public OrderModel updateOrder(@PathVariable String id, @RequestBody OrderModel updates) {
        return orderService.updateOrder(id, updates);
    }

    // POST /api/orders/{id}/cancel — Cancel an order
    @PostMapping("/{id}/cancel")
    public void cancelOrder(@PathVariable String id) {
        orderService.cancelOrder(id);
        try {
            robotDispatchService.controlTask(id, "cancel");
        } catch (Exception e) {
            // Ignore if task doesn't exist on DCS
        }
    }

    // POST /api/orders/{id}/cancel-delivery — Cancel delivery and return to charging point
    @PostMapping("/{id}/cancel-delivery")
    public void cancelDelivery(@PathVariable String id) {
        // Cancel the current task on DCS
        try {
            robotDispatchService.controlTask(id, "cancel");
        } catch (Exception e) {
            // Ignore if task doesn't exist on DCS
        }

        // Return robot to charging point
        try {
            // Assuming default robot and site for now, or fetch from order if available
            robotDispatchService.returnToChargingPoint(null, null);
        } catch (Exception e) {
            // Ignore if dispatch fails
        }

        // Mark order as cancelled
        orderService.cancelOrder(id);
    }

    // POST /api/orders/{id}/pause — Pause a task
    @PostMapping("/{id}/pause")
    public void pauseTask(@PathVariable String id) {
        robotDispatchService.controlTask(id, "pause");
    }

    // POST /api/orders/{id}/recover — Recover a task
    @PostMapping("/{id}/recover")
    public void recoverTask(@PathVariable String id) {
        robotDispatchService.controlTask(id, "recover");
    }

    public record StatusUpdateRequest(String status, Integer compartmentId) {}
}
