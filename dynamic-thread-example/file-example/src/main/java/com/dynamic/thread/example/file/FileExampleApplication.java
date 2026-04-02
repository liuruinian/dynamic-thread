package com.dynamic.thread.example.file;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * File Configuration Example Application
 * 
 * This example demonstrates how to use local file as the configuration source
 * for dynamic thread pool.
 */
@SpringBootApplication
public class FileExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileExampleApplication.class, args);
    }
}
