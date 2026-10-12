package com.smarthome.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "devices")
public class Device {

    @Id
    @Column(name = "device_id", nullable = false, unique = true)
    private String deviceId;

    @Column(nullable = false)
    private String name;

    @Column(name = "device_type", nullable = false)
    private String deviceType;

    @Column(nullable = false)
    private String room;

    @Column(nullable = false)
    private String status = "OFF";

    @Column(name = "is_online", nullable = false)
    private boolean isOnline = true;

    @Column(name = "registered_at", nullable = false)
    private String registeredAt;

    @Column(name = "updated_at", nullable = false)
    private String updatedAt;

    public Device() {
        this.registeredAt = Instant.now().toString();
        this.updatedAt = Instant.now().toString();
    }

    public Device(String deviceId, String name, String deviceType, String room, String status) {
        this.deviceId = deviceId;
        this.name = name;
        this.deviceType = deviceType;
        this.room = room;
        this.status = status;
        this.isOnline = true;
        this.registeredAt = Instant.now().toString();
        this.updatedAt = Instant.now().toString();
    }

    // Getters and Setters
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }

    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }

    public String getStatus() { return status; }
    public void setStatus(String status) { 
        this.status = status; 
        this.updatedAt = Instant.now().toString();
    }

    public boolean isOnline() { return isOnline; }
    public void setOnline(boolean online) { isOnline = online; }

    public String getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(String registeredAt) { this.registeredAt = registeredAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
