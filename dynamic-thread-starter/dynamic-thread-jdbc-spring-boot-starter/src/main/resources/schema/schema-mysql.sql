-- Dynamic Thread Pool Configuration Table
-- Compatible with MySQL, PostgreSQL, H2 and other databases

CREATE TABLE IF NOT EXISTS dynamic_thread_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    config_key VARCHAR(128) NOT NULL COMMENT 'Configuration key identifier',
    config_content TEXT NOT NULL COMMENT 'Thread pool configuration content (YAML/JSON/Properties)',
    config_type VARCHAR(32) DEFAULT 'YAML' COMMENT 'Configuration content type: YAML, JSON, PROPERTIES',
    version BIGINT DEFAULT 1 COMMENT 'Configuration version number',
    enabled TINYINT DEFAULT 1 COMMENT 'Whether this configuration is enabled: 1=enabled, 0=disabled',
    description VARCHAR(512) COMMENT 'Configuration description',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Dynamic thread pool configuration table';

-- Example configuration data
INSERT INTO dynamic_thread_config (config_key, config_content, config_type, version, enabled, description) VALUES
('default', 
'dynamic-thread:
  executors:
    - thread-pool-id: order-executor
      core-pool-size: 10
      maximum-pool-size: 20
      queue-capacity: 1000
      keep-alive-time: 60
      rejected-handler: CallerRunsPolicy
    - thread-pool-id: payment-executor
      core-pool-size: 5
      maximum-pool-size: 10
      queue-capacity: 500
      keep-alive-time: 120
      rejected-handler: AbortPolicy',
'YAML', 1, 1, 'Default thread pool configuration');
