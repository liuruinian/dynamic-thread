-- Initial configuration data
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
      alarm:
        enable: true
        queue-threshold: 80
        active-threshold: 90
    - thread-pool-id: payment-executor
      core-pool-size: 5
      maximum-pool-size: 10
      queue-capacity: 500
      keep-alive-time: 120
      rejected-handler: AbortPolicy
      alarm:
        enable: true
        queue-threshold: 70
        active-threshold: 85',
'YAML', 1, 1, 'Default thread pool configuration');
