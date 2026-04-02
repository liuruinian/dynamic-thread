package com.dynamic.thread.server.controller;

import com.dynamic.thread.server.handler.ServerChannelHandler;
import com.dynamic.thread.server.registry.ClientRegistry;
import com.dynamic.thread.server.registry.ClientRegistry.WebContainerState;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API controller for managing web container thread pools across all connected applications.
 * 
 * <p>Provides endpoints for:
 * <ul>
 *   <li>Listing all web container thread pools from connected agents</li>
 *   <li>Getting web container states by app or instance</li>
 *   <li>Summary statistics across all applications</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/web-containers")
@RequiredArgsConstructor
public class WebContainerController {

    private final ClientRegistry clientRegistry;
    private final ServerChannelHandler channelHandler;
    private final ObjectMapper objectMapper;

    /**
     * Get all web container thread pool states from all connected agents
     */
    @GetMapping("/states")
    public ResponseEntity<List<WebContainerState>> getAllStates() {
        List<WebContainerState> states = clientRegistry.getAllWebContainerStates();
        return ResponseEntity.ok(states);
    }

    /**
     * Get web container states grouped by application
     */
    @GetMapping("/states/grouped")
    public ResponseEntity<Map<String, List<WebContainerState>>> getStatesGroupedByApp() {
        List<WebContainerState> allStates = clientRegistry.getAllWebContainerStates();
        
        Map<String, List<WebContainerState>> grouped = allStates.stream()
                .collect(Collectors.groupingBy(WebContainerState::getAppId));
        
        return ResponseEntity.ok(grouped);
    }

    /**
     * Get web container states for a specific application
     */
    @GetMapping("/apps/{appId}/states")
    public ResponseEntity<List<WebContainerState>> getStatesByApp(@PathVariable String appId) {
        List<WebContainerState> states = clientRegistry.getWebContainerStatesByApp(appId);
        return ResponseEntity.ok(states);
    }

    /**
     * Get web container state for a specific instance
     */
    @GetMapping("/instances/{instanceId}/state")
    public ResponseEntity<WebContainerState> getStateByInstance(@PathVariable String instanceId) {
        WebContainerState state = clientRegistry.getWebContainerState(instanceId);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(state);
    }

    /**
     * Get summary statistics for all web containers
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        List<WebContainerState> allStates = clientRegistry.getAllWebContainerStates();
        
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalInstances", allStates.size());
        summary.put("onlineInstances", allStates.stream().filter(WebContainerState::isOnline).count());
        
        // Group by container type
        Map<String, Long> byContainerType = allStates.stream()
                .collect(Collectors.groupingBy(WebContainerState::getContainerType, Collectors.counting()));
        summary.put("byContainerType", byContainerType);
        
        // Group by app
        Map<String, Long> byApp = allStates.stream()
                .collect(Collectors.groupingBy(WebContainerState::getAppId, Collectors.counting()));
        summary.put("byApp", byApp);
        
        // Calculate average metrics
        if (!allStates.isEmpty()) {
            double avgActivePercent = allStates.stream()
                    .filter(s -> s.getState() != null && s.getState().getActivePercent() != null)
                    .mapToDouble(s -> s.getState().getActivePercent())
                    .average()
                    .orElse(0.0);
            summary.put("avgActivePercent", Math.round(avgActivePercent * 100.0) / 100.0);
            
            double avgQueueUsage = allStates.stream()
                    .filter(s -> s.getState() != null && s.getState().getQueueUsagePercent() != null)
                    .mapToDouble(s -> s.getState().getQueueUsagePercent())
                    .average()
                    .orElse(0.0);
            summary.put("avgQueueUsagePercent", Math.round(avgQueueUsage * 100.0) / 100.0);
        }
        
        return ResponseEntity.ok(summary);
    }

    /**
     * Get list of available apps with web containers
     */
    @GetMapping("/apps")
    public ResponseEntity<List<Map<String, Object>>> listApps() {
        List<WebContainerState> allStates = clientRegistry.getAllWebContainerStates();
        
        Map<String, List<WebContainerState>> byApp = allStates.stream()
                .collect(Collectors.groupingBy(WebContainerState::getAppId));
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<WebContainerState>> entry : byApp.entrySet()) {
            Map<String, Object> appInfo = new HashMap<>();
            appInfo.put("appId", entry.getKey());
            appInfo.put("instanceCount", entry.getValue().size());
            appInfo.put("onlineCount", entry.getValue().stream().filter(WebContainerState::isOnline).count());
            
            // Get container types used by this app
            Set<String> containerTypes = entry.getValue().stream()
                    .map(WebContainerState::getContainerType)
                    .collect(Collectors.toSet());
            appInfo.put("containerTypes", containerTypes);
            
            result.add(appInfo);
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * Update web container thread pool configuration for a specific instance
     */
    @PostMapping("/instances/{instanceId}/config")
    public ResponseEntity<Map<String, Object>> updateConfig(
            @PathVariable String instanceId,
            @RequestBody Map<String, Object> config) {
        
        Map<String, Object> result = new HashMap<>();
        result.put("instanceId", instanceId);
        
        // Verify instance exists and is online
        WebContainerState state = clientRegistry.getWebContainerState(instanceId);
        if (state == null) {
            result.put("success", false);
            result.put("message", "Instance not found: " + instanceId);
            return ResponseEntity.badRequest().body(result);
        }
        
        if (!state.isOnline()) {
            result.put("success", false);
            result.put("message", "Instance is offline: " + instanceId);
            return ResponseEntity.badRequest().body(result);
        }
        
        try {
            String configJson = objectMapper.writeValueAsString(config);
            boolean sent = channelHandler.sendWebContainerConfigUpdate(instanceId, configJson);
            
            if (sent) {
                result.put("success", true);
                result.put("message", "Configuration update sent successfully");
                log.info("Web container config update sent to instance {}: {}", instanceId, config);
            } else {
                result.put("success", false);
                result.put("message", "Failed to send configuration update, client may be disconnected");
            }
        } catch (Exception e) {
            log.error("Failed to send web container config update to {}: {}", instanceId, e.getMessage());
            result.put("success", false);
            result.put("message", "Error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
        
        return ResponseEntity.ok(result);
    }
}
