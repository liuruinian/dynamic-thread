package com.dynamic.thread.example.etcd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ETCD Configuration Example Application
 * 
 * This example demonstrates how to use ETCD as the configuration center
 * for dynamic thread pool.
 */
@SpringBootApplication
public class EtcdExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(EtcdExampleApplication.class, args);
    }
}
