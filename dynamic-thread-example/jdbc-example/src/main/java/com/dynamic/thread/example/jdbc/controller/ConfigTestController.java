package com.dynamic.thread.example.jdbc.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class ConfigTestController {

    private final JdbcTemplate jdbcTemplate;

    public ConfigTestController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/update-config")
    public String updateConfig() {
        String newConfig = """
            dynamic-thread:
              executors:
                - thread-pool-id: order-executor
                  core-pool-size: 15
                  maximum-pool-size: 30
                  queue-capacity: 2000
                  keep-alive-time: 120
                  rejected-handler: CallerRunsPolicy
                - thread-pool-id: payment-executor
                  core-pool-size: 8
                  maximum-pool-size: 16
                  queue-capacity: 800
                  keep-alive-time: 60
                  rejected-handler: AbortPolicy
            """.stripIndent();
        
        int updated = jdbcTemplate.update(
            "UPDATE dynamic_thread_config SET config_content = ? WHERE config_key = ?",
            newConfig, "default"
        );
        
        return "Updated " + updated + " row(s). Wait for next poll cycle (10s) to see changes.";
    }
}
