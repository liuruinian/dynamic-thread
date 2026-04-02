package com.dynamic.thread.starter.jdbc.listener;

import com.dynamic.thread.spring.properties.DynamicThreadPoolProperties;
import com.dynamic.thread.starter.common.listener.AbstractConfigChangeListener;
import com.dynamic.thread.starter.common.refresher.ThreadPoolRefresher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * JDBC database configuration change listener.
 * Polls database for configuration changes and triggers thread pool refresh.
 */
@Slf4j
public class JdbcConfigChangeListener extends AbstractConfigChangeListener {

    private final JdbcTemplate jdbcTemplate;
    private final DynamicThreadPoolProperties properties;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicReference<String> lastConfigHash = new AtomicReference<>("");

    public JdbcConfigChangeListener(ThreadPoolRefresher refresher,
                                    JdbcTemplate jdbcTemplate,
                                    DynamicThreadPoolProperties properties) {
        super(refresher);
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Override
    public void startListening() {
        DynamicThreadPoolProperties.JdbcProperties jdbcProps = properties.getJdbc();
        if (jdbcProps == null) {
            log.warn("JDBC properties not configured, skipping JDBC listener registration");
            return;
        }

        String tableName = jdbcProps.getTableName();
        String configKey = jdbcProps.getConfigKey();

        log.info("JDBC config listener starting for table: {}, key: {}", tableName, configKey);

        // Load initial configuration
        loadConfiguration();

        initialized.set(true);
        log.info("JDBC config listener started successfully");
    }

    @Override
    public void stopListening() {
        initialized.set(false);
        log.info("JDBC config listener stopped");
    }

    /**
     * Scheduled task to poll for configuration changes.
     * The poll interval is controlled by dynamic-thread.jdbc.poll-interval property.
     */
    @Scheduled(fixedDelayString = "${dynamic-thread.jdbc.poll-interval:30000}")
    public void pollForChanges() {
        if (!initialized.get()) {
            return;
        }

        try {
            loadConfiguration();
        } catch (Exception e) {
            log.error("Failed to poll configuration from database", e);
        }
    }

    /**
     * Load configuration from database and trigger refresh if changed
     */
    private void loadConfiguration() {
        DynamicThreadPoolProperties.JdbcProperties jdbcProps = properties.getJdbc();
        if (jdbcProps == null) {
            return;
        }

        String tableName = jdbcProps.getTableName();
        String configKey = jdbcProps.getConfigKey();

        try {
            String sql = String.format(
                    "SELECT config_content, config_type FROM %s WHERE config_key = ? AND enabled = 1 ORDER BY version DESC LIMIT 1",
                    tableName);

            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, configKey);

            if (results.isEmpty()) {
                log.debug("No configuration found for key: {}", configKey);
                return;
            }

            Map<String, Object> row = results.get(0);
            String configContent = (String) row.get("config_content");
            String configType = (String) row.get("config_type");

            if (configContent == null || configContent.isBlank()) {
                log.debug("Configuration content is empty for key: {}", configKey);
                return;
            }

            // Check if configuration has changed using MD5 hash
            String currentHash = md5Hash(configContent);
            String previousHash = lastConfigHash.get();

            if (!currentHash.equals(previousHash)) {
                log.info("Configuration changed in database, refreshing thread pools. Key: {}", configKey);
                lastConfigHash.set(currentHash);
                onConfigChange(configContent, configType != null ? configType : "YAML");
            }

        } catch (Exception e) {
            log.error("Failed to load configuration from database", e);
        }
    }

    /**
     * Calculate MD5 hash of content for change detection
     */
    private String md5Hash(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(content.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // Fallback to simple hash
            return String.valueOf(content.hashCode());
        }
    }
}
