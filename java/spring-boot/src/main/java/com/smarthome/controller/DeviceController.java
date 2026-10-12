package com.smarthome.controller;

import com.smarthome.dto.DeviceControlRequest;
import com.smarthome.dto.DeviceRegisterRequest;
import com.smarthome.model.Device;
import com.smarthome.model.DeviceActivity;
import com.smarthome.repository.DeviceActivityRepository;
import com.smarthome.repository.DeviceRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/devices")
@CrossOrigin(origins = "*")
public class DeviceController {

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DeviceActivityRepository activityRepository;

    // 1. REGISTER DEVICE
    @PostMapping
    public ResponseEntity<?> registerDevice(@Valid @RequestBody DeviceRegisterRequest request) {
        if (deviceRepository.existsById(request.getDevice_id())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, 
                "Device with ID '" + request.getDevice_id() + "' is already registered."
            );
        }

        String initialStatus = (request.getInitial_status() != null && request.getInitial_status().equalsIgnoreCase("ON")) ? "ON" : "OFF";
        Device device = new Device(
            request.getDevice_id(),
            request.getName(),
            request.getDevice_type(),
            request.getRoom(),
            initialStatus
        );
        Device saved = deviceRepository.save(device);

        // Record activity log
        activityRepository.save(new DeviceActivity(
            saved.getDeviceId(),
            "REGISTER",
            null,
            initialStatus,
            "User",
            "Device registered in " + saved.getRoom()
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // 2. GET DEVICE STATUS
    @GetMapping("/{deviceId}/status")
    public ResponseEntity<?> getDeviceStatus(@PathVariable String deviceId) {
        Device device = deviceRepository.findById(deviceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device '" + deviceId + "' not found."));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("device_id", device.getDeviceId());
        response.put("name", device.getName());
        response.put("status", device.getStatus());
        response.put("is_online", device.isOnline());
        response.put("last_updated", device.getUpdatedAt());

        return ResponseEntity.ok(response);
    }

    // 3. CONTROL DEVICE (TURN ON / OFF)
    @PostMapping("/{deviceId}/control")
    public ResponseEntity<?> controlDevice(
            @PathVariable String deviceId,
            @Valid @RequestBody DeviceControlRequest request) {

        Device device = deviceRepository.findById(deviceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device '" + deviceId + "' not found."));

        if (!device.isOnline()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, 
                "Cannot control device '" + deviceId + "' because it is currently OFFLINE."
            );
        }

        String previousStatus = device.getStatus();
        String newStatus = request.getCommand().toUpperCase();
        device.setStatus(newStatus);
        deviceRepository.save(device);

        // Record activity log
        activityRepository.save(new DeviceActivity(
            deviceId,
            "COMMAND_" + newStatus,
            previousStatus,
            newStatus,
            request.getPerformed_by(),
            "Device turned " + newStatus + " via API"
        ));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Device " + deviceId + " successfully turned " + newStatus + ".");
        response.put("device_id", deviceId);
        response.put("previous_status", previousStatus);
        response.put("current_status", newStatus);
        response.put("timestamp", device.getUpdatedAt());

        return ResponseEntity.ok(response);
    }

    // 4. GET DEVICE ACTIVITY
    @GetMapping("/{deviceId}/activity")
    public ResponseEntity<?> getDeviceActivity(@PathVariable String deviceId) {
        if (!deviceRepository.existsById(deviceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Device '" + deviceId + "' not found.");
        }

        List<DeviceActivity> logs = activityRepository.findByDeviceIdOrderByActivityIdDesc(deviceId);
        return ResponseEntity.ok(logs);
    }

    // 5. REMOVE DEVICE
    @DeleteMapping("/{deviceId}")
    @Transactional
    public ResponseEntity<?> removeDevice(@PathVariable String deviceId) {
        if (!deviceRepository.existsById(deviceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Device '" + deviceId + "' not found.");
        }

        activityRepository.deleteByDeviceId(deviceId);
        deviceRepository.deleteById(deviceId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Device '" + deviceId + "' and all associated logs were successfully removed.");
        response.put("device_id", deviceId);

        return ResponseEntity.ok(response);
    }

    // HELPER: LIST ALL DEVICES
    @GetMapping
    public ResponseEntity<?> listAllDevices() {
        return ResponseEntity.ok(deviceRepository.findAll());
    }
}
