package com.vtmen.backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import java.time.LocalDateTime;

@Document(collection = "orders")
public class OrderModel {

    @Id
    private String id;
    private String orderCode;
    private String pinCode;
    private String fullName;
    private String phone;
    private String address;
    // Campus / place name for robot destination (optional; e.g. from location picker).
    private String destinationName;
    // DCS / Mongo {@code maps} document id this order was created against (JSON {@code map_name}).
    @JsonProperty("map_name")
    @Field("map_name")
    private String mapName;
    private Integer quantity;
    private String note;
    private String status; // pending, placed, shipping, delivered, cancelled
    private LocalDateTime createdTime;
    private LocalDateTime completedTime;
    private Integer compartmentId;
    private LocalDateTime depositedTime;
    private LocalDateTime arrivalTime;
    private String robotId;
    private String capacityResourceId;
    private String capacityResourceName;

    public OrderModel() {
        this.createdTime = LocalDateTime.now();
    }

    // --- Getters & Setters ---

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOrderCode() {
        return orderCode;
    }

    public void setOrderCode(String orderCode) {
        this.orderCode = orderCode;
    }

    public String getPinCode() {
        return pinCode;
    }

    public void setPinCode(String pinCode) {
        this.pinCode = pinCode;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getDestinationName() {
        return destinationName;
    }

    public void setDestinationName(String destinationName) {
        this.destinationName = destinationName;
    }

    public String getMapName() {
        return mapName;
    }

    public void setMapName(String mapName) {
        this.mapName = mapName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public LocalDateTime getCompletedTime() {
        return completedTime;
    }

    public void setCompletedTime(LocalDateTime completedTime) {
        this.completedTime = completedTime;
    }

    public Integer getCompartmentId() {
        return compartmentId;
    }

    public void setCompartmentId(Integer compartmentId) {
        this.compartmentId = compartmentId;
    }

    public LocalDateTime getDepositedTime() {
        return depositedTime;
    }

    public void setDepositedTime(LocalDateTime depositedTime) {
        this.depositedTime = depositedTime;
    }

    public LocalDateTime getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(LocalDateTime arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public String getRobotId() {
        return robotId;
    }

    public void setRobotId(String robotId) {
        this.robotId = robotId;
    }

    public String getCapacityResourceId() {
        return capacityResourceId;
    }

    public void setCapacityResourceId(String capacityResourceId) {
        this.capacityResourceId = capacityResourceId;
    }

    public String getCapacityResourceName() {
        return capacityResourceName;
    }

    public void setCapacityResourceName(String capacityResourceName) {
        this.capacityResourceName = capacityResourceName;
    }
}
