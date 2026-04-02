package com.dynamic.thread.core.notify;

import com.dynamic.thread.core.enums.NotifyPlatformEnum;
import com.dynamic.thread.core.model.ThreadPoolState;
import com.dynamic.thread.core.util.CommonComponents;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * WeChatWork (企业微信) notification platform implementation.
 * Sends notifications via WeChatWork Robot Webhook.
 */
@Slf4j
public class WeChatWorkNotifyPlatform extends AbstractHttpNotifyPlatform {

    public WeChatWorkNotifyPlatform(String webhookUrl) {
        super(webhookUrl);
    }

    @Override
    public String getType() {
        return NotifyPlatformEnum.WECHAT.getCode();
    }

    @Override
    protected String getPlatformName() {
        return "WeChatWork";
    }

    @Override
    protected String buildRequestBody(String content) throws Exception {
        Map<String, Object> markdown = new HashMap<>();
        markdown.put("content", content);

        Map<String, Object> body = new HashMap<>();
        body.put("msgtype", "markdown");
        body.put("markdown", markdown);

        return CommonComponents.objectMapper().writeValueAsString(body);
    }

    @Override
    protected boolean isSuccessResponse(int statusCode, String responseBody) {
        if (statusCode != 200 || responseBody == null) {
            return false;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = CommonComponents.objectMapper().readValue(responseBody, Map.class);
            Integer errcode = (Integer) result.get("errcode");
            return errcode != null && errcode == 0;
        } catch (Exception e) {
            log.warn("[WeChatWork] Failed to parse response: {}", e.getMessage());
            return false;
        }
    }

    @Override
    protected String buildAlarmContent(ThreadPoolState state, String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Dynamic Thread Pool Alarm\n\n");
        sb.append("> **Thread Pool:** <font color=\"warning\">").append(state.getThreadPoolId()).append("</font>\n");
        sb.append("> **Alarm Message:** ").append(message).append("\n\n");
        sb.append("### Thread Pool State\n");
        sb.append("> Core Pool Size: **").append(state.getCorePoolSize()).append("**\n");
        sb.append("> Max Pool Size: **").append(state.getMaximumPoolSize()).append("**\n");
        sb.append("> Active Count: **").append(state.getActiveCount()).append("**\n");
        sb.append("> Queue Size: **").append(state.getQueueSize()).append(" / ").append(state.getQueueCapacity()).append("**\n");
        sb.append("> Queue Usage: <font color=\"warning\">").append(String.format("%.1f%%", state.getQueueUsagePercent())).append("</font>\n");
        sb.append("> Thread Usage: <font color=\"warning\">").append(String.format("%.1f%%", state.getActivePercent())).append("</font>\n");
        sb.append("> Rejected Count: **").append(state.getRejectedCount()).append("**\n\n");
        sb.append("> Alarm Time: ").append(state.getTimestamp().format(FORMATTER));
        return sb.toString();
    }

    @Override
    protected String buildConfigChangeContent(String threadPoolId, String oldConfig, String newConfig) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Thread Pool Config Changed\n\n");
        sb.append("> **Thread Pool:** <font color=\"info\">").append(threadPoolId).append("</font>\n\n");
        sb.append("### Before\n");
        sb.append("```\n").append(oldConfig).append("\n```\n\n");
        sb.append("### After\n");
        sb.append("```\n").append(newConfig).append("\n```\n\n");
        sb.append("> Change Time: ").append(java.time.LocalDateTime.now().format(FORMATTER));
        return sb.toString();
    }
}
