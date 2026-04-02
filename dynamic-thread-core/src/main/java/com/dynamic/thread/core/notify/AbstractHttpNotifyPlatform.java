package com.dynamic.thread.core.notify;

import com.dynamic.thread.core.model.ThreadPoolState;
import com.dynamic.thread.core.util.CommonComponents;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Abstract base class for HTTP-based notification platforms.
 * Provides common functionality for sending HTTP notifications.
 * Implements Template Method pattern for notification sending.
 */
@Slf4j
public abstract class AbstractHttpNotifyPlatform implements NotifyPlatform {

    protected static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    protected final String webhookUrl;

    protected AbstractHttpNotifyPlatform(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    /**
     * Get the platform name for logging purposes.
     *
     * @return the platform name
     */
    protected abstract String getPlatformName();

    /**
     * Build the JSON request body for the notification.
     *
     * @param content the notification content
     * @return the JSON body string
     * @throws Exception if building the body fails
     */
    protected abstract String buildRequestBody(String content) throws Exception;

    /**
     * Check if the response indicates success.
     *
     * @param statusCode the HTTP status code
     * @param responseBody the response body
     * @return true if the request was successful
     */
    protected abstract boolean isSuccessResponse(int statusCode, String responseBody);

    /**
     * Build alarm content for the specific platform format.
     *
     * @param state the thread pool state
     * @param message the alarm message
     * @return the formatted content string
     */
    protected abstract String buildAlarmContent(ThreadPoolState state, String message);

    /**
     * Build config change content for the specific platform format.
     *
     * @param threadPoolId the thread pool id
     * @param oldConfig the old configuration
     * @param newConfig the new configuration
     * @return the formatted content string
     */
    protected abstract String buildConfigChangeContent(String threadPoolId, String oldConfig, String newConfig);

    @Override
    public boolean sendAlarm(ThreadPoolState state, String message) {
        String content = buildAlarmContent(state, message);
        return sendNotification(content);
    }

    @Override
    public boolean sendConfigChange(String threadPoolId, String oldConfig, String newConfig) {
        String content = buildConfigChangeContent(threadPoolId, oldConfig, newConfig);
        return sendNotification(content);
    }

    /**
     * Send notification using the common HTTP sending logic.
     * This is the template method that defines the algorithm structure.
     *
     * @param content the notification content
     * @return true if sent successfully
     */
    protected boolean sendNotification(String content) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("[{}] Webhook URL is not configured", getPlatformName());
            return false;
        }

        try {
            String jsonBody = buildRequestBody(content);
            String targetUrl = buildTargetUrl();

            log.debug("[{}] Sending notification to {}", getPlatformName(), targetUrl);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(CommonComponents.defaultRequestTimeout())
                    .build();

            HttpResponse<String> response = CommonComponents.httpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (isSuccessResponse(response.statusCode(), response.body())) {
                log.info("[{}] Notification sent successfully", getPlatformName());
                return true;
            } else {
                log.warn("[{}] Notification failed, status={}, response={}",
                        getPlatformName(), response.statusCode(), response.body());
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[{}] Sending interrupted", getPlatformName());
            return false;
        } catch (Exception e) {
            log.error("[{}] Failed to send notification: {}", getPlatformName(), e.getMessage());
            return false;
        }
    }

    /**
     * Build the target URL for the request.
     * Subclasses can override this to add signatures or other parameters.
     *
     * @return the target URL
     */
    protected String buildTargetUrl() {
        return webhookUrl;
    }

    /**
     * Add custom headers to the request.
     * Subclasses can override this to add authentication or other headers.
     *
     * @param builder the request builder
     * @return the modified request builder
     */
    protected HttpRequest.Builder addCustomHeaders(HttpRequest.Builder builder) {
        return builder;
    }

    @Override
    public Map<String, String> getConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("webhookUrl", webhookUrl);
        config.put("platform", getType());
        return config;
    }

    /**
     * Escape special characters for JSON string value.
     *
     * @param text the text to escape
     * @return the escaped text
     */
    protected String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Build common alarm state table content (generic format).
     *
     * @param state the thread pool state
     * @return the formatted state information
     */
    protected String buildCommonStateInfo(ThreadPoolState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("Core Pool Size: ").append(state.getCorePoolSize()).append("\n");
        sb.append("Max Pool Size: ").append(state.getMaximumPoolSize()).append("\n");
        sb.append("Active Count: ").append(state.getActiveCount()).append("\n");
        sb.append("Queue Size: ").append(state.getQueueSize()).append(" / ").append(state.getQueueCapacity()).append("\n");
        sb.append("Queue Usage: ").append(String.format("%.1f%%", state.getQueueUsagePercent())).append("\n");
        sb.append("Thread Usage: ").append(String.format("%.1f%%", state.getActivePercent())).append("\n");
        sb.append("Completed Tasks: ").append(state.getCompletedTaskCount()).append("\n");
        sb.append("Rejected Count: ").append(state.getRejectedCount()).append("\n");
        return sb.toString();
    }
}
