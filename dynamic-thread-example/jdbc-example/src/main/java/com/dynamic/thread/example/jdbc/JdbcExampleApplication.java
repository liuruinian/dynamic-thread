package com.dynamic.thread.example.jdbc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * JDBC Configuration Example Application
 * 
 * This example demonstrates how to use database as the configuration source
 * for dynamic thread pool.
 */
@SpringBootApplication
public class JdbcExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(JdbcExampleApplication.class, args);
    }
}
