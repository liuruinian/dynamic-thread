package com.dynamic.thread.server.controller;

import com.dynamic.thread.server.model.ServerMetrics;
import com.dynamic.thread.server.model.ServerMetrics.*;
import com.dynamic.thread.server.service.ServerMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        return Map.of(
                "code", 200,
                "message", "success",
                "data", serverMetricsService.getMetrics()
        );
    }

    /**
     * Get CPU information.
     */
    @GetMapping("/cpu")
    public Map<String, Object> getCpuInfo() {
        return Map.of(
                "code", 200,
                "message", "success",
                "data", serverMetricsService.getCpuInfo()
        );
    }

    /**
     * Get memory information.
     */
    @GetMapping("/memory")
    public Map<String, Object> getMemoryInfo() {
        return Map.of(
                "code", 200,
                "message", "success",
                "data", serverMetricsService.getMemoryInfo()
        );
    }

    /**
     * Get disk information.
     */
    @GetMapping("/disk")
    public Map<String, Object> getDiskInfo() {
        return Map.of(
                "code", 200,
                "message", "success",
                "data", serverMetricsService.getDiskInfo()
        );
    }

    /**
     * Get network information.
     */
    @GetMapping("/network")
    public Map<String, Object> getNetworkInfo() {
        return Map.of(
                "code", 200,
                "message", "success",
                "data", serverMetricsService.getNetworkInfo()
        );
    }

    /**
     * Get JVM information.
     */
    @GetMapping("/jvm")
    public Map<String, Object> getJvmInfo() {
        return Map.of(
                "code", 200,
                "message", "success",
                "data", serverMetricsService.getJvmInfo()
        );
    }
}
