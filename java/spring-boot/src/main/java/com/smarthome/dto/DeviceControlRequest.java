package com.smarthome.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class DeviceControlRequest {

    @NotBlank(message = "command is required")
    @Pattern(regexp = "(?i)^(ON|OFF)$", message = "command must be 'ON' or 'OFF'")
    private String command;

    private String performed_by = "Mobile App User";

    // Getters and Setters
    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }

    public String getPerformed_by() { return performed_by; }
    public void setPerformed_by(String performed_by) { this.performed_by = performed_by; }
}
