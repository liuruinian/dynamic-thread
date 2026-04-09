package com.dynamic.thread.server.controller;

import com.dynamic.thread.server.model.ServerMetrics;
import com.dynamic.thread.server.model.ServerMetrics.*;
import com.dynamic.thread.server.service.ServerMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * REST API controller for server metrics.
 * Provides endpoints to retrieve CPU, memory, disk, network and JVM information.
 */
@RestController
@RequestMapping("/api/server")
@RequiredArgsConstructor
public class ServerMetricsController {

    private final ServerMetricsService serverMetricsService;

    /**
     * Get all server metrics.
     */
    @GetMapping("/metrics")
    public Map<String, Object> getMetrics() {
        return buildResponse(serverMetricsService.getMetrics());
    }

    /**
     * Get CPU information.
     */
    @GetMapping("/cpu")
    public Map<String, Object> getCpuInfo() {
        return buildResponse(serverMetricsService.getCpuInfo());
    }

    /**
     * Get memory information.
     */
    @GetMapping("/memory")
    public Map<String, Object> getMemoryInfo() {
        return buildResponse(serverMetricsService.getMemoryInfo());
    }

    /**
     * Get disk information.
     */
    @GetMapping("/disk")
    public Map<String, Object> getDiskInfo() {
        return buildResponse(serverMetricsService.getDiskInfo());
    }

    /**
     * Get network information.
     */
    @GetMapping("/network")
    public Map<String, Object> getNetworkInfo() {
        return buildResponse(serverMetricsService.getNetworkInfo());
    }

    /**
     * Get JVM information.
     */
    @GetMapping("/jvm")
    public Map<String, Object> getJvmInfo() {
        return buildResponse(serverMetricsService.getJvmInfo());
    }

    private Map<String, Object> buildResponse(Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "success");
        result.put("data", data);
        return result;
    }
}
