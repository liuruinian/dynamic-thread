-- Schema for H2 database
CREATE TABLE IF NOT EXISTS dynamic_thread_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    config_key VARCHAR(128) NOT NULL,
    config_content TEXT NOT NULL,
    config_type VARCHAR(32) DEFAULT 'YAML',
    version BIGINT DEFAULT 1,
    enabled TINYINT DEFAULT 1,
    description VARCHAR(512),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_config_key UNIQUE (config_key)
);
