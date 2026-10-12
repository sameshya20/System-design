package com.smarthome.dto;

import jakarta.validation.constraints.NotBlank;

public class DeviceRegisterRequest {

    @NotBlank(message = "device_id is required")
    private String device_id;

    @NotBlank(message = "name is required")
    private String name;

    @NotBlank(message = "device_type is required")
    private String device_type;

    @NotBlank(message = "room is required")
    private String room;

    private String initial_status = "OFF";

    // Getters and Setters
    public String getDevice_id() { return device_id; }
    public void setDevice_id(String device_id) { this.device_id = device_id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDevice_type() { return device_type; }
    public void setDevice_type(String device_type) { this.device_type = device_type; }

    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }

    public String getInitial_status() { return initial_status; }
    public void setInitial_status(String initial_status) { this.initial_status = initial_status; }
}
