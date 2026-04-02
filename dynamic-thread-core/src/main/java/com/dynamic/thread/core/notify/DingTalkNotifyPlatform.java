package com.dynamic.thread.core.notify;

import com.dynamic.thread.core.enums.NotifyPlatformEnum;
import com.dynamic.thread.core.model.ThreadPoolState;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * DingTalk notification platform implementation.
 * Supports HMAC-SHA256 signing for secure webhook calls.
 */
@Slf4j
public class DingTalkNotifyPlatform extends AbstractHttpNotifyPlatform {

    private final String secret;

    public DingTalkNotifyPlatform(String webhookUrl) {
        this(webhookUrl, null);
    }

    public DingTalkNotifyPlatform(String webhookUrl, String secret) {
        super(webhookUrl);
        this.secret = secret;
    }

    @Override
    public String getType() {
        return NotifyPlatformEnum.DING.getCode();
    }

    @Override
    protected String getPlatformName() {
        return "DingTalk";
    }

    @Override
    public Map<String, String> getConfig() {
        Map<String, String> config = super.getConfig();
        if (secret != null && !secret.isBlank()) {
            // Mask secret for security: show first 6 and last 4 chars
            String masked = secret.length() > 10
                    ? secret.substring(0, 6) + "****" + secret.substring(secret.length() - 4)
                    : "****";
            config.put("secret", masked);
            config.put("hasSecret", "true");
        } else {
            config.put("hasSecret", "false");
        }
        return config;
    }

    @Override
    protected String buildRequestBody(String content) {
        String escapedContent = escapeJson(content);
        return "{\"msgtype\":\"markdown\",\"markdown\":{\"title\":\"Thread Pool Notification\",\"text\":\"" + escapedContent + "\"}}";
    }

    @Override
    protected boolean isSuccessResponse(int statusCode, String responseBody) {
        return statusCode == 200 && responseBody != null && responseBody.contains("\"errcode\":0");
    }

    @Override
    protected String buildTargetUrl() {
        if (secret == null || secret.isBlank()) {
            return webhookUrl;
        }

        try {
            long timestamp = System.currentTimeMillis();
            String stringToSign = timestamp + "\n" + secret;

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            String sign = URLEncoder.encode(Base64.getEncoder().encodeToString(signData), StandardCharsets.UTF_8);

            String separator = webhookUrl.contains("?") ? "&" : "?";
            return webhookUrl + separator + "timestamp=" + timestamp + "&sign=" + sign;
        } catch (Exception e) {
            log.error("[DingTalk] Failed to generate signature: {}", e.getMessage());
            return webhookUrl;
        }
    }

    @Override
    protected String buildAlarmContent(ThreadPoolState state, String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("### Dynamic Thread Pool Alarm\n\n");
        sb.append("> **Thread Pool:** ").append(state.getThreadPoolId()).append("\n\n");
        sb.append("> **Alarm Message:** ").append(message).append("\n\n");
        sb.append("---\n\n");
        sb.append("#### Thread Pool State\n\n");
        sb.append("| Metric | Value |\n");
        sb.append("|:---|:---|\n");
        sb.append("| Core Pool Size | ").append(state.getCorePoolSize()).append(" |\n");
        sb.append("| Max Pool Size | ").append(state.getMaximumPoolSize()).append(" |\n");
        sb.append("| Active Count | ").append(state.getActiveCount()).append(" |\n");
        sb.append("| Queue Size | ").append(state.getQueueSize()).append(" / ").append(state.getQueueCapacity()).append(" |\n");
        sb.append("| Queue Usage | ").append(String.format("%.1f%%", state.getQueueUsagePercent())).append(" |\n");
        sb.append("| Thread Usage | ").append(String.format("%.1f%%", state.getActivePercent())).append(" |\n");
        sb.append("| Completed Tasks | ").append(state.getCompletedTaskCount()).append(" |\n");
        sb.append("| Rejected Count | ").append(state.getRejectedCount()).append(" |\n");
        sb.append("\n");
        sb.append("> Alarm Time: ").append(state.getTimestamp().format(FORMATTER));
        return sb.toString();
    }

    @Override
    protected String buildConfigChangeContent(String threadPoolId, String oldConfig, String newConfig) {
        StringBuilder sb = new StringBuilder();
        sb.append("### Thread Pool Config Changed\n\n");
        sb.append("> **Thread Pool:** ").append(threadPoolId).append("\n\n");
        sb.append("---\n\n");
        sb.append("#### Old Config\n\n").append(oldConfig).append("\n\n");
        sb.append("#### New Config\n\n").append(newConfig);
        return sb.toString();
    }
}
