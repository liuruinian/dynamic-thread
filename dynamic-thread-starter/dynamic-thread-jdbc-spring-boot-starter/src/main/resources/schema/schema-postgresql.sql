-- Dynamic Thread Pool Configuration Table for PostgreSQL

CREATE TABLE IF NOT EXISTS dynamic_thread_config (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(128) NOT NULL,
    config_content TEXT NOT NULL,
    config_type VARCHAR(32) DEFAULT 'YAML',
    version BIGINT DEFAULT 1,
    enabled SMALLINT DEFAULT 1,
    description VARCHAR(512),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_config_key UNIQUE (config_key)
);

COMMENT ON TABLE dynamic_thread_config IS 'Dynamic thread pool configuration table';
COMMENT ON COLUMN dynamic_thread_config.config_key IS 'Configuration key identifier';
COMMENT ON COLUMN dynamic_thread_config.config_content IS 'Thread pool configuration content (YAML/JSON/Properties)';
COMMENT ON COLUMN dynamic_thread_config.config_type IS 'Configuration content type: YAML, JSON, PROPERTIES';
COMMENT ON COLUMN dynamic_thread_config.version IS 'Configuration version number';
COMMENT ON COLUMN dynamic_thread_config.enabled IS 'Whether this configuration is enabled: 1=enabled, 0=disabled';
COMMENT ON COLUMN dynamic_thread_config.description IS 'Configuration description';

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
