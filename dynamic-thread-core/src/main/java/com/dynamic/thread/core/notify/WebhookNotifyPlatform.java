package com.dynamic.thread.core.notify;

import com.dynamic.thread.core.enums.NotifyPlatformEnum;
import com.dynamic.thread.core.model.ThreadPoolState;
import com.dynamic.thread.core.util.CommonComponents;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Webhook notification platform implementation.
 * Sends notifications to a custom HTTP endpoint with optional HMAC signature.
 */
@Slf4j
public class WebhookNotifyPlatform extends AbstractHttpNotifyPlatform {

    private final String secret;

    public WebhookNotifyPlatform(String webhookUrl) {
        this(webhookUrl, null);
    }

    public WebhookNotifyPlatform(String webhookUrl, String secret) {
        super(webhookUrl);
        this.secret = secret;
    }

    @Override
    public String getType() {
        return NotifyPlatformEnum.WEBHOOK.getCode();
    }

    @Override
    protected String getPlatformName() {
        return "Webhook";
    }

    @Override
    public Map<String, String> getConfig() {
        Map<String, String> config = super.getConfig();
        config.put("hasSecret", secret != null && !secret.trim().isEmpty() ? "true" : "false");
        return config;
    }

    @Override
    public boolean sendAlarm(ThreadPoolState state, String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "ALARM");
        payload.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        payload.put("threadPoolId", state.getThreadPoolId());
        payload.put("message", message);
        payload.put("state", buildStateMap(state));

        return sendWebhook(payload);
    }

    @Override
    public boolean sendConfigChange(String threadPoolId, String oldConfig, String newConfig) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "CONFIG_CHANGE");
        payload.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        payload.put("threadPoolId", threadPoolId);
        payload.put("oldConfig", oldConfig);
        payload.put("newConfig", newConfig);

        return sendWebhook(payload);
    }

    /**
     * Send webhook request with custom payload
     */
    private boolean sendWebhook(Map<String, Object> payload) {
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            log.warn("[Webhook] Webhook URL is not configured");
            return false;
        }

        try {
            String jsonBody = CommonComponents.objectMapper().writeValueAsString(payload);

            HttpRequest request = HttpRequest.post(webhookUrl)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "DynamicThreadPool-Webhook/1.0")
                    .timeout(CommonComponents.defaultRequestTimeoutMs());

            // Add authorization header if secret is provided
            if (secret != null && !secret.isEmpty()) {
                request.header("Authorization", "Bearer " + secret);
                request.header("X-Signature", generateSignature(jsonBody));
            }

            HttpResponse response = request.body(jsonBody).execute();

            if (response.getStatus() >= 200 && response.getStatus() < 300) {
                log.info("[Webhook] Notification sent successfully to {}", webhookUrl);
                return true;
            } else {
                log.warn("[Webhook] Notification failed with status {}: {}",
                        response.getStatus(), response.body());
                return false;
            }
        } catch (Exception e) {
            log.error("[Webhook] Failed to send notification: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Generate HMAC-SHA256 signature for request verification
     */
    private String generateSignature(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.warn("[Webhook] Failed to generate signature: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Build state map from ThreadPoolState
     */
    private Map<String, Object> buildStateMap(ThreadPoolState state) {
        Map<String, Object> stateMap = new HashMap<>();
        stateMap.put("threadPoolId", state.getThreadPoolId());
        stateMap.put("corePoolSize", state.getCorePoolSize());
        stateMap.put("maximumPoolSize", state.getMaximumPoolSize());
        stateMap.put("poolSize", state.getPoolSize());
        stateMap.put("activeCount", state.getActiveCount());
        stateMap.put("queueSize", state.getQueueSize());
        stateMap.put("queueCapacity", state.getQueueCapacity());
        stateMap.put("queueUsagePercent", state.getQueueUsagePercent());
        stateMap.put("activePercent", state.getActivePercent());
        stateMap.put("completedTaskCount", state.getCompletedTaskCount());
        stateMap.put("rejectedCount", state.getRejectedCount());
        if (state.getTimestamp() != null) {
            stateMap.put("timestamp", state.getTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        return stateMap;
    }

    // These methods are not used for Webhook as it uses JSON payload directly
    @Override
    protected String buildRequestBody(String content) throws Exception {
        throw new UnsupportedOperationException("Webhook uses custom payload structure");
    }

    @Override
    protected boolean isSuccessResponse(int statusCode, String responseBody) {
        return statusCode >= 200 && statusCode < 300;
    }

    @Override
    protected String buildAlarmContent(ThreadPoolState state, String message) {
        // Not used for Webhook platform
        return "";
    }

    @Override
    protected String buildConfigChangeContent(String threadPoolId, String oldConfig, String newConfig) {
        // Not used for Webhook platform
        return "";
    }
}
