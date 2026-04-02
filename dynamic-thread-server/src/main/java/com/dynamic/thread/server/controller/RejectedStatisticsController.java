package com.dynamic.thread.server.controller;

import com.dynamic.thread.core.model.ThreadPoolState;
import com.dynamic.thread.core.protocol.Message;
import com.dynamic.thread.server.registry.ClientRegistry;
import io.netty.channel.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API controller for rejection statistics and monitoring.
 * Aggregates rejection data from all connected client instances.
 */
@Slf4j
@RestController
@RequestMapping("/api/reject")
@RequiredArgsConstructor
public class RejectedStatisticsController {

    private final ClientRegistry clientRegistry;
    
    /**
     * Flag to enable/disable instant alarm on rejection
     */
    private volatile boolean instantAlarmEnabled = true;

    // ==================== Statistics ====================

    /**
     * Get global rejection statistics summary from all clients
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        Map<String, Object> result = new HashMap<>();
        
        // Aggregate from all client states
        Map<String, Long> poolStatistics = new LinkedHashMap<>();
        long globalRejectedCount = 0;
        
        Map<String, Map<String, List<ThreadPoolState>>> allStates = clientRegistry.getAllStates();
        
        for (Map<String, List<ThreadPoolState>> appInstances : allStates.values()) {
            for (List<ThreadPoolState> states : appInstances.values()) {
                for (ThreadPoolState state : states) {
                    String poolId = state.getThreadPoolId();
                    long rejected = state.getRejectedCount() != null ? state.getRejectedCount() : 0;
                    
                    poolStatistics.merge(poolId, rejected, Long::sum);
                    globalRejectedCount += rejected;
                }
            }
        }
        
        result.put("success", true);
        result.put("globalRejectedCount", globalRejectedCount);
        result.put("poolCount", poolStatistics.size());
        result.put("poolStatistics", poolStatistics);
        result.put("instantAlarmEnabled", this.instantAlarmEnabled);
        
        return ResponseEntity.ok(result);
    }

    /**
     * Get rejection statistics for a specific pool across all instances
     */
    @GetMapping("/statistics/{threadPoolId}")
    public ResponseEntity<Map<String, Object>> getPoolStatistics(@PathVariable String threadPoolId) {
        Map<String, Object> result = new HashMap<>();
        
        long totalRejected = 0;
        List<Map<String, Object>> instanceDetails = new ArrayList<>();
        
        Map<String, Map<String, List<ThreadPoolState>>> allStates = clientRegistry.getAllStates();
        
        for (Map.Entry<String, Map<String, List<ThreadPoolState>>> appEntry : allStates.entrySet()) {
            String appId = appEntry.getKey();
            for (Map.Entry<String, List<ThreadPoolState>> instanceEntry : appEntry.getValue().entrySet()) {
                String instanceId = instanceEntry.getKey();
                for (ThreadPoolState state : instanceEntry.getValue()) {
                    if (threadPoolId.equals(state.getThreadPoolId())) {
                        long rejected = state.getRejectedCount() != null ? state.getRejectedCount() : 0;
                        totalRejected += rejected;
                        
                        Map<String, Object> detail = new HashMap<>();
                        detail.put("appId", appId);
                        detail.put("instanceId", instanceId);
                        detail.put("rejectedCount", rejected);
                        detail.put("rejectedHandler", state.getRejectedHandler());
                        detail.put("timestamp", state.getTimestamp());
                        instanceDetails.add(detail);
                    }
                }
            }
        }
        
        result.put("success", true);
        result.put("threadPoolId", threadPoolId);
        result.put("totalRejectedCount", totalRejected);
        result.put("instanceCount", instanceDetails.size());
        result.put("instances", instanceDetails);
        
        return ResponseEntity.ok(result);
    }

    // ==================== Pool Details ====================

    /**
     * Get all pools with their rejection counts and details
     */
    @GetMapping("/pools")
    public ResponseEntity<Map<String, Object>> getAllPoolDetails() {
        Map<String, Object> result = new HashMap<>();
        
        List<Map<String, Object>> poolList = new ArrayList<>();
        long globalRejectedCount = 0;
        
        Map<String, Map<String, List<ThreadPoolState>>> allStates = clientRegistry.getAllStates();
        
        // Group by threadPoolId
        Map<String, List<ThreadPoolState>> poolStates = new LinkedHashMap<>();
        
        for (Map<String, List<ThreadPoolState>> appInstances : allStates.values()) {
            for (List<ThreadPoolState> states : appInstances.values()) {
                for (ThreadPoolState state : states) {
                    poolStates.computeIfAbsent(state.getThreadPoolId(), k -> new ArrayList<>()).add(state);
                }
            }
        }
        
        for (Map.Entry<String, List<ThreadPoolState>> entry : poolStates.entrySet()) {
            String poolId = entry.getKey();
            List<ThreadPoolState> states = entry.getValue();
            
            long totalRejected = states.stream()
                    .mapToLong(s -> s.getRejectedCount() != null ? s.getRejectedCount() : 0)
                    .sum();
            globalRejectedCount += totalRejected;
            
            // Get the most recent state for details
            ThreadPoolState latestState = states.stream()
                    .max(Comparator.comparing(s -> s.getTimestamp() != null ? s.getTimestamp() : java.time.LocalDateTime.MIN))
                    .orElse(states.get(0));
            
            Map<String, Object> poolInfo = new HashMap<>();
            poolInfo.put("threadPoolId", poolId);
            poolInfo.put("rejectedCount", totalRejected);
            poolInfo.put("rejectedHandler", latestState.getRejectedHandler());
            poolInfo.put("instanceCount", states.size());
            poolInfo.put("corePoolSize", latestState.getCorePoolSize());
            poolInfo.put("maximumPoolSize", latestState.getMaximumPoolSize());
            poolInfo.put("activeCount", latestState.getActiveCount());
            poolInfo.put("queueSize", latestState.getQueueSize());
            poolInfo.put("queueCapacity", latestState.getQueueCapacity());
            poolInfo.put("timestamp", latestState.getTimestamp());
            
            poolList.add(poolInfo);
        }
        
        // Sort by rejected count descending
        poolList.sort((a, b) -> Long.compare(
                (Long) b.get("rejectedCount"), 
                (Long) a.get("rejectedCount")
        ));
        
        result.put("success", true);
        result.put("pools", poolList);
        result.put("poolCount", poolList.size());
        result.put("globalRejectedCount", globalRejectedCount);
        
        return ResponseEntity.ok(result);
    }

    // ==================== Records (Simulated from current state) ====================

    /**
     * Get recent rejection records for all pools
     * Note: These are derived from current state snapshots since detailed records 
     * are stored on client side only
     */
    @GetMapping("/records")
    public ResponseEntity<Map<String, Object>> getRecentRecords(
            @RequestParam(defaultValue = "20") int limit) {
        Map<String, Object> result = new HashMap<>();
        
        List<Map<String, Object>> records = new ArrayList<>();
        Map<String, Map<String, List<ThreadPoolState>>> allStates = clientRegistry.getAllStates();
        
        for (Map.Entry<String, Map<String, List<ThreadPoolState>>> appEntry : allStates.entrySet()) {
            String appId = appEntry.getKey();
            for (Map.Entry<String, List<ThreadPoolState>> instanceEntry : appEntry.getValue().entrySet()) {
                String instanceId = instanceEntry.getKey();
                for (ThreadPoolState state : instanceEntry.getValue()) {
                    if (state.getRejectedCount() != null && state.getRejectedCount() > 0) {
                        Map<String, Object> record = new HashMap<>();
                        record.put("threadPoolId", state.getThreadPoolId());
                        record.put("appId", appId);
                        record.put("instanceId", instanceId);
                        record.put("rejectedCount", state.getRejectedCount());
                        record.put("rejectedPolicy", state.getRejectedHandler());
                        record.put("corePoolSize", state.getCorePoolSize());
                        record.put("maximumPoolSize", state.getMaximumPoolSize());
                        record.put("activeCount", state.getActiveCount());
                        record.put("queueSize", state.getQueueSize());
                        record.put("queueCapacity", state.getQueueCapacity());
                        record.put("queueUsagePercent", state.getQueueUsagePercent());
                        record.put("activePercent", state.getActivePercent());
                        record.put("timestamp", state.getTimestamp());
                        records.add(record);
                    }
                }
            }
        }
        
        // Sort by rejected count descending
        records.sort((a, b) -> Long.compare(
                ((Number) b.get("rejectedCount")).longValue(),
                ((Number) a.get("rejectedCount")).longValue()
        ));
        
        // Limit results
        if (records.size() > limit) {
            records = records.subList(0, limit);
        }
        
        result.put("success", true);
        result.put("records", records);
        result.put("totalRecords", records.size());
        
        return ResponseEntity.ok(result);
    }

    /**
     * Get rejection details for a specific pool
     */
    @GetMapping("/records/{threadPoolId}")
    public ResponseEntity<Map<String, Object>> getPoolRecords(
            @PathVariable String threadPoolId,
            @RequestParam(defaultValue = "50") int limit) {
        Map<String, Object> result = new HashMap<>();
        
        List<Map<String, Object>> records = new ArrayList<>();
        Map<String, Map<String, List<ThreadPoolState>>> allStates = clientRegistry.getAllStates();
        
        for (Map.Entry<String, Map<String, List<ThreadPoolState>>> appEntry : allStates.entrySet()) {
            String appId = appEntry.getKey();
            for (Map.Entry<String, List<ThreadPoolState>> instanceEntry : appEntry.getValue().entrySet()) {
                String instanceId = instanceEntry.getKey();
                for (ThreadPoolState state : instanceEntry.getValue()) {
                    if (threadPoolId.equals(state.getThreadPoolId())) {
                        Map<String, Object> record = new HashMap<>();
                        record.put("threadPoolId", state.getThreadPoolId());
                        record.put("appId", appId);
                        record.put("instanceId", instanceId);
                        record.put("rejectedCount", state.getRejectedCount() != null ? state.getRejectedCount() : 0);
                        record.put("rejectedPolicy", state.getRejectedHandler());
                        record.put("corePoolSize", state.getCorePoolSize());
                        record.put("maximumPoolSize", state.getMaximumPoolSize());
                        record.put("activeCount", state.getActiveCount());
                        record.put("queueSize", state.getQueueSize());
                        record.put("queueCapacity", state.getQueueCapacity());
                        record.put("queueUsagePercent", state.getQueueUsagePercent());
                        record.put("activePercent", state.getActivePercent());
                        record.put("timestamp", state.getTimestamp());
                        records.add(record);
                    }
                }
            }
        }
        
        result.put("success", true);
        result.put("threadPoolId", threadPoolId);
        result.put("records", records);
        result.put("count", records.size());
        
        return ResponseEntity.ok(result);
    }

    // ==================== Summary ====================

    /**
     * Get comprehensive rejection summary
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        Map<String, Object> result = new HashMap<>();
        
        // Build summary from all client states
        Map<String, Long> poolStatistics = new LinkedHashMap<>();
        long globalRejectedCount = 0;
        int appCount = 0;
        int instanceCount = 0;
        
        Map<String, Map<String, List<ThreadPoolState>>> allStates = clientRegistry.getAllStates();
        appCount = allStates.size();
        
        for (Map<String, List<ThreadPoolState>> appInstances : allStates.values()) {
            instanceCount += appInstances.size();
            for (List<ThreadPoolState> states : appInstances.values()) {
                for (ThreadPoolState state : states) {
                    String poolId = state.getThreadPoolId();
                    long rejected = state.getRejectedCount() != null ? state.getRejectedCount() : 0;
                    
                    poolStatistics.merge(poolId, rejected, Long::sum);
                    globalRejectedCount += rejected;
                }
            }
        }
        
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("globalRejectedCount", globalRejectedCount);
        summary.put("poolCount", poolStatistics.size());
        summary.put("appCount", appCount);
        summary.put("instanceCount", instanceCount);
        summary.put("poolStatistics", poolStatistics);
        summary.put("instantAlarmEnabled", instantAlarmEnabled);
        
        result.put("success", true);
        result.put("summary", summary);
        
        return ResponseEntity.ok(result);
    }

    // ==================== Configuration ====================

    /**
     * Enable or disable instant alarm on rejection
     */
    @PostMapping("/instant-alarm")
    public ResponseEntity<Map<String, Object>> toggleInstantAlarm(
            @RequestParam boolean enabled) {
        Map<String, Object> result = new HashMap<>();
        
        this.instantAlarmEnabled = enabled;
        
        result.put("success", true);
        result.put("instantAlarmEnabled", enabled);
        result.put("message", enabled ? "即时告警已启用" : "即时告警已禁用");
        
        log.info("Instant alarm {}", enabled ? "enabled" : "disabled");
        return ResponseEntity.ok(result);
    }

    // ==================== Reset ====================

    /**
     * Reset statistics for a specific pool
     * This clears local cache and sends reset command to all connected clients
     */
    @PostMapping("/reset/{threadPoolId}")
    public ResponseEntity<Map<String, Object>> resetPoolStatistics(
            @PathVariable String threadPoolId) {
        Map<String, Object> result = new HashMap<>();
        
        // 1. Clear local cache
        clientRegistry.resetPoolRejectionStats(threadPoolId);
        
        // 2. Send reset command to all clients that have this pool
        Map<String, Channel> channels = clientRegistry.getChannelsForPool(threadPoolId);
        int sentCount = 0;
        int failedCount = 0;
        
        for (Map.Entry<String, Channel> entry : channels.entrySet()) {
            String instanceId = entry.getKey();
            Channel channel = entry.getValue();
            
            try {
                ClientRegistry.ClientInfo clientInfo = clientRegistry.getClient(instanceId);
                if (clientInfo != null && channel.isActive()) {
                    Message resetMsg = Message.resetRejectStats(
                            clientInfo.getAppId(), 
                            instanceId, 
                            threadPoolId
                    );
                    channel.writeAndFlush(resetMsg);
                    sentCount++;
                    log.debug("Sent reset command to instance: {}", instanceId);
                }
            } catch (Exception e) {
                failedCount++;
                log.warn("Failed to send reset command to instance: {}", instanceId, e);
            }
        }
        
        result.put("success", true);
        result.put("threadPoolId", threadPoolId);
        result.put("clientsNotified", sentCount);
        result.put("clientsFailed", failedCount);
        result.put("message", String.format("已重置线程池 %s 的统计数据，通知了 %d 个客户端", threadPoolId, sentCount));
        
        log.info("Reset rejection statistics for pool: {}, notified {} clients", threadPoolId, sentCount);
        return ResponseEntity.ok(result);
    }

    /**
     * Reset all statistics
     * This clears all local cache and sends reset command to all connected clients
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetAllStatistics() {
        Map<String, Object> result = new HashMap<>();
        
        // 1. Clear all local cache
        clientRegistry.resetAllRejectionStats();
        
        // 2. Send reset command to all online clients
        Map<String, Channel> channels = clientRegistry.getAllOnlineChannels();
        int sentCount = 0;
        int failedCount = 0;
        
        for (Map.Entry<String, Channel> entry : channels.entrySet()) {
            String instanceId = entry.getKey();
            Channel channel = entry.getValue();
            
            try {
                ClientRegistry.ClientInfo clientInfo = clientRegistry.getClient(instanceId);
                if (clientInfo != null && channel.isActive()) {
                    Message resetMsg = Message.resetRejectStats(
                            clientInfo.getAppId(), 
                            instanceId, 
                            null  // null means reset all pools
                    );
                    channel.writeAndFlush(resetMsg);
                    sentCount++;
                    log.debug("Sent reset all command to instance: {}", instanceId);
                }
            } catch (Exception e) {
                failedCount++;
                log.warn("Failed to send reset command to instance: {}", instanceId, e);
            }
        }
        
        result.put("success", true);
        result.put("clientsNotified", sentCount);
        result.put("clientsFailed", failedCount);
        result.put("message", String.format("已重置所有统计数据，通知了 %d 个客户端", sentCount));
        
        log.info("Reset all rejection statistics, notified {} clients", sentCount);
        return ResponseEntity.ok(result);
    }
}
