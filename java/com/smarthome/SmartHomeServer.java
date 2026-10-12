package com.smarthome;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Smart Home Device Management Platform
 * Standalone Java REST API Server (Built with Java 21 Standard Library)
 * No external dependencies required! Runs directly with:
 *   javac SmartHomeServer.java
 *   java com.smarthome.SmartHomeServer
 */
public class SmartHomeServer {

    private static final int PORT = 8080;

    // In-memory thread-safe data stores
    private static final Map<String, Device> deviceDatabase = new ConcurrentHashMap<>();
    private static final Map<String, List<ActivityLog>> activityDatabase = new ConcurrentHashMap<>();
    private static final AtomicInteger activityIdCounter = new AtomicInteger(1);

    // =========================================================================
    // DATA MODELS
    // =========================================================================
    public static class Device {
        public String deviceId;
        public String name;
        public String deviceType;
        public String room;
        public String status;      // "ON" or "OFF"
        public boolean isOnline;
        public String registeredAt;
        public String updatedAt;

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

        public String toJson() {
            return String.format(
                "{\"device_id\":\"%s\",\"name\":\"%s\",\"device_type\":\"%s\",\"room\":\"%s\",\"status\":\"%s\",\"is_online\":%b,\"registered_at\":\"%s\",\"updated_at\":\"%s\"}",
                escapeJson(deviceId), escapeJson(name), escapeJson(deviceType), escapeJson(room),
                escapeJson(status), isOnline, registeredAt, updatedAt
            );
        }
    }

    public static class ActivityLog {
        public int activityId;
        public String deviceId;
        public String action;
        public String previousStatus;
        public String newStatus;
        public String performedBy;
        public String timestamp;
        public String details;

        public ActivityLog(int activityId, String deviceId, String action, String previousStatus,
                           String newStatus, String performedBy, String details) {
            this.activityId = activityId;
            this.deviceId = deviceId;
            this.action = action;
            this.previousStatus = previousStatus;
            this.newStatus = newStatus;
            this.performedBy = performedBy;
            this.timestamp = Instant.now().toString();
            this.details = details;
        }

        public String toJson() {
            return String.format(
                "{\"activity_id\":%d,\"device_id\":\"%s\",\"action\":\"%s\",\"previous_status\":%s,\"new_status\":\"%s\",\"performed_by\":\"%s\",\"timestamp\":\"%s\",\"details\":\"%s\"}",
                activityId, escapeJson(deviceId), escapeJson(action),
                previousStatus == null ? "null" : "\"" + escapeJson(previousStatus) + "\"",
                escapeJson(newStatus), escapeJson(performedBy), timestamp, escapeJson(details)
            );
        }
    }

    // =========================================================================
    // MAIN ENTRY POINT
    // =========================================================================
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);

        // Register Route Handlers
        server.createContext("/", new RootHandler());
        server.createContext("/devices", new DevicesHandler());

        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor()); // Java 21 Virtual Threads!
        System.out.println("=============================================================");
        System.out.println(" Smart Home Device Management Platform (Java 21)");
        System.out.println(" Server running on: http://127.0.0.1:" + PORT);
        System.out.println("=============================================================");
        server.start();
    }

    // =========================================================================
    // HTTP HANDLERS
    // =========================================================================
    static class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (exchange.getRequestURI().getPath().equals("/")) {
                String response = "{\"status\":\"online\",\"system\":\"Smart Home Device Management Platform (Java 21)\",\"version\":\"1.0.0\"}";
                sendJsonResponse(exchange, 200, response);
            } else {
                sendJsonResponse(exchange, 404, "{\"detail\":\"Resource not found\"}");
            }
        }
    }

    static class DevicesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod().toUpperCase();

            // Enable CORS headers
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            try {
                // Route 1: POST /devices (Register Device)
                if (path.equals("/devices") && "POST".equals(method)) {
                    handleRegisterDevice(exchange);
                    return;
                }

                // Route 2: GET /devices (List all devices)
                if (path.equals("/devices") && "GET".equals(method)) {
                    handleListDevices(exchange);
                    return;
                }

                // Sub-routes under /devices/{id}/...
                String[] parts = path.split("/");
                // Expected format: /devices/{deviceId} or /devices/{deviceId}/{action}
                if (parts.length >= 3) {
                    String deviceId = parts[2];

                    // Route 3: DELETE /devices/{device_id} (Remove Device)
                    if (parts.length == 3 && "DELETE".equals(method)) {
                        handleRemoveDevice(exchange, deviceId);
                        return;
                    }

                    // Route 4: GET /devices/{device_id}/status (Get Device Status)
                    if (parts.length == 4 && "status".equals(parts[3]) && "GET".equals(method)) {
                        handleGetDeviceStatus(exchange, deviceId);
                        return;
                    }

                    // Route 5: POST /devices/{device_id}/control (Control Device - Turn ON/OFF)
                    if (parts.length == 4 && "control".equals(parts[3]) && "POST".equals(method)) {
                        handleControlDevice(exchange, deviceId);
                        return;
                    }

                    // Route 6: GET /devices/{device_id}/activity (Get Device Activity Logs)
                    if (parts.length == 4 && "activity".equals(parts[3]) && "GET".equals(method)) {
                        handleGetDeviceActivity(exchange, deviceId);
                        return;
                    }
                }

                sendJsonResponse(exchange, 404, "{\"detail\":\"Endpoint not found: " + path + "\"}");
            } catch (Exception e) {
                e.printStackTrace();
                sendJsonResponse(exchange, 500, "{\"detail\":\"Internal server error: " + escapeJson(e.getMessage()) + "\"}");
            }
        }

        // 1. REGISTER DEVICE (POST /devices)
        private void handleRegisterDevice(HttpExchange exchange) throws IOException {
            String body = readBody(exchange);
            Map<String, String> json = parseJsonMap(body);

            String deviceId = json.get("device_id");
            String name = json.get("name");
            String deviceType = json.get("device_type");
            String room = json.get("room");
            String initialStatus = json.getOrDefault("initial_status", "OFF").toUpperCase();

            // Validation
            if (deviceId == null || deviceId.isBlank() || name == null || name.isBlank() ||
                deviceType == null || deviceType.isBlank() || room == null || room.isBlank()) {
                sendJsonResponse(exchange, 422, "{\"detail\":\"Missing required fields: device_id, name, device_type, room\"}");
                return;
            }

            if (!initialStatus.equals("ON") && !initialStatus.equals("OFF")) {
                sendJsonResponse(exchange, 422, "{\"detail\":\"initial_status must be 'ON' or 'OFF'\"}");
                return;
            }

            // Check duplicate
            if (deviceDatabase.containsKey(deviceId)) {
                sendJsonResponse(exchange, 400, "{\"detail\":\"Device with ID '" + deviceId + "' is already registered.\"}");
                return;
            }

            // Save device
            Device device = new Device(deviceId, name, deviceType, room, initialStatus);
            deviceDatabase.put(deviceId, device);

            // Record registration activity
            List<ActivityLog> logs = activityDatabase.computeIfAbsent(deviceId, k -> new CopyOnWriteArrayList<>());
            logs.add(new ActivityLog(
                activityIdCounter.getAndIncrement(),
                deviceId,
                "REGISTER",
                null,
                initialStatus,
                "User",
                "Device registered in " + room
            ));

            sendJsonResponse(exchange, 201, device.toJson());
        }

        // 2. GET DEVICE STATUS (GET /devices/{id}/status)
        private void handleGetDeviceStatus(HttpExchange exchange, String deviceId) throws IOException {
            Device device = deviceDatabase.get(deviceId);
            if (device == null) {
                sendJsonResponse(exchange, 404, "{\"detail\":\"Device '" + deviceId + "' not found.\"}");
                return;
            }

            String response = String.format(
                "{\"device_id\":\"%s\",\"name\":\"%s\",\"status\":\"%s\",\"is_online\":%b,\"last_updated\":\"%s\"}",
                escapeJson(device.deviceId), escapeJson(device.name),
                escapeJson(device.status), device.isOnline, device.updatedAt
            );
            sendJsonResponse(exchange, 200, response);
        }

        // 3. CONTROL DEVICE (POST /devices/{id}/control)
        private void handleControlDevice(HttpExchange exchange, String deviceId) throws IOException {
            Device device = deviceDatabase.get(deviceId);
            if (device == null) {
                sendJsonResponse(exchange, 404, "{\"detail\":\"Device '" + deviceId + "' not found.\"}");
                return;
            }

            if (!device.isOnline) {
                sendJsonResponse(exchange, 400, "{\"detail\":\"Cannot control device '" + deviceId + "' because it is currently OFFLINE.\"}");
                return;
            }

            String body = readBody(exchange);
            Map<String, String> json = parseJsonMap(body);
            String command = json.get("command");
            String performedBy = json.getOrDefault("performed_by", "Mobile App User");

            if (command == null || (!command.equalsIgnoreCase("ON") && !command.equalsIgnoreCase("OFF"))) {
                sendJsonResponse(exchange, 422, "{\"detail\":\"Invalid command. Expected 'ON' or 'OFF'.\"}");
                return;
            }

            String previousStatus = device.status;
            String newStatus = command.toUpperCase();
            device.status = newStatus;
            device.updatedAt = Instant.now().toString();

            // Record activity
            List<ActivityLog> logs = activityDatabase.computeIfAbsent(deviceId, k -> new CopyOnWriteArrayList<>());
            logs.add(new ActivityLog(
                activityIdCounter.getAndIncrement(),
                deviceId,
                "COMMAND_" + newStatus,
                previousStatus,
                newStatus,
                performedBy,
                "Device turned " + newStatus + " via API"
            ));

            String response = String.format(
                "{\"message\":\"Device %s successfully turned %s.\",\"device_id\":\"%s\",\"previous_status\":\"%s\",\"current_status\":\"%s\",\"timestamp\":\"%s\"}",
                escapeJson(deviceId), newStatus, escapeJson(deviceId),
                previousStatus, newStatus, device.updatedAt
            );
            sendJsonResponse(exchange, 200, response);
        }

        // 4. GET DEVICE ACTIVITY (GET /devices/{id}/activity)
        private void handleGetDeviceActivity(HttpExchange exchange, String deviceId) throws IOException {
            if (!deviceDatabase.containsKey(deviceId)) {
                sendJsonResponse(exchange, 404, "{\"detail\":\"Device '" + deviceId + "' not found.\"}");
                return;
            }

            List<ActivityLog> logs = activityDatabase.getOrDefault(deviceId, Collections.emptyList());
            StringBuilder sb = new StringBuilder("[");
            // Reverse order (most recent first)
            for (int i = logs.size() - 1; i >= 0; i--) {
                sb.append(logs.get(i).toJson());
                if (i > 0) sb.append(",");
            }
            sb.append("]");

            sendJsonResponse(exchange, 200, sb.toString());
        }

        // 5. REMOVE DEVICE (DELETE /devices/{id})
        private void handleRemoveDevice(HttpExchange exchange, String deviceId) throws IOException {
            if (!deviceDatabase.containsKey(deviceId)) {
                sendJsonResponse(exchange, 404, "{\"detail\":\"Device '" + deviceId + "' not found.\"}");
                return;
            }

            deviceDatabase.remove(deviceId);
            activityDatabase.remove(deviceId);

            String response = String.format(
                "{\"message\":\"Device '%s' and all associated logs were successfully removed.\",\"device_id\":\"%s\"}",
                escapeJson(deviceId), escapeJson(deviceId)
            );
            sendJsonResponse(exchange, 200, response);
        }

        // 6. LIST ALL DEVICES (GET /devices)
        private void handleListDevices(HttpExchange exchange) throws IOException {
            StringBuilder sb = new StringBuilder("[");
            int count = 0;
            for (Device device : deviceDatabase.values()) {
                if (count > 0) sb.append(",");
                sb.append(device.toJson());
                count++;
            }
            sb.append("]");
            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    // =========================================================================
    // UTILITY METHODS
    // =========================================================================
    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String responseBody) throws IOException {
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> parseJsonMap(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.isBlank()) return map;

        Pattern pattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(?:\"([^\"]*)\"|([true|false|null|\\d\\.]+))");
        Matcher matcher = pattern.matcher(json);
        while (matcher.find()) {
            String key = matcher.group(1);
            String strVal = matcher.group(2);
            String nonStrVal = matcher.group(3);
            map.put(key, strVal != null ? strVal : nonStrVal);
        }
        return map;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
