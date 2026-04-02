package com.dynamic.thread.starter.file.listener;

import com.dynamic.thread.spring.properties.DynamicThreadPoolProperties;
import com.dynamic.thread.starter.common.listener.AbstractConfigChangeListener;
import com.dynamic.thread.starter.common.refresher.ThreadPoolRefresher;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Local file configuration change listener.
 * Uses WatchService to monitor file changes and triggers thread pool refresh.
 */
@Slf4j
public class FileConfigChangeListener extends AbstractConfigChangeListener {

    private final DynamicThreadPoolProperties properties;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private WatchService watchService;
    private Thread watchThread;
    private Path configFilePath;

    public FileConfigChangeListener(ThreadPoolRefresher refresher,
                                    DynamicThreadPoolProperties properties) {
        super(refresher);
        this.properties = properties;
    }

    @Override
    public void startListening() {
        DynamicThreadPoolProperties.FileProperties fileProps = properties.getFile();
        if (fileProps == null || fileProps.getPath() == null || fileProps.getPath().isBlank()) {
            log.warn("File path is not configured, skipping file listener registration");
            return;
        }

        configFilePath = Paths.get(fileProps.getPath());
        if (!Files.exists(configFilePath)) {
            log.warn("Configuration file does not exist: {}", configFilePath);
            return;
        }

        try {
            // Create watch service
            watchService = FileSystems.getDefault().newWatchService();

            // Register the parent directory for watching
            Path parentDir = configFilePath.getParent();
            if (parentDir != null) {
                parentDir.register(watchService,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_CREATE);
                log.info("File watcher registered for directory: {}", parentDir);
            }

            // Load initial configuration
            loadConfiguration();

            // Start watch thread
            running.set(true);
            watchThread = new Thread(this::watchLoop, "dynamic-thread-file-watcher");
            watchThread.setDaemon(true);
            watchThread.start();

            log.info("File config listener started for: {}", configFilePath);

        } catch (IOException e) {
            log.error("Failed to start file watcher", e);
        }
    }

    @Override
    public void stopListening() {
        running.set(false);

        if (watchThread != null) {
            watchThread.interrupt();
            watchThread = null;
        }

        if (watchService != null) {
            try {
                watchService.close();
                watchService = null;
                log.info("File config listener stopped");
            } catch (IOException e) {
                log.error("Failed to close watch service", e);
            }
        }
    }

    /**
     * Watch loop for file changes
     */
    private void watchLoop() {
        while (running.get()) {
            try {
                WatchKey key = watchService.take();

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path changedFile = pathEvent.context();

                    // Check if the changed file is our config file
                    if (configFilePath.getFileName().equals(changedFile)) {
                        log.info("Configuration file changed: {}, event: {}", changedFile, kind);

                        // Small delay to ensure file write is complete
                        Thread.sleep(100);

                        loadConfiguration();
                    }
                }

                // Reset the key to receive further events
                boolean valid = key.reset();
                if (!valid) {
                    log.warn("Watch key is no longer valid, stopping watcher");
                    break;
                }

            } catch (InterruptedException e) {
                if (running.get()) {
                    log.debug("File watcher interrupted");
                }
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                log.debug("Watch service closed");
                break;
            } catch (Exception e) {
                log.error("Error in file watch loop", e);
            }
        }
    }

    /**
     * Load configuration from file and trigger refresh
     */
    private void loadConfiguration() {
        try {
            if (!Files.exists(configFilePath)) {
                log.warn("Configuration file not found: {}", configFilePath);
                return;
            }

            String content = Files.readString(configFilePath);
            if (content == null || content.isBlank()) {
                log.warn("Configuration file is empty: {}", configFilePath);
                return;
            }

            DynamicThreadPoolProperties.FileProperties fileProps = properties.getFile();
            String configType = fileProps.getConfigType() != null 
                    ? fileProps.getConfigType() 
                    : detectConfigType(configFilePath.toString());

            log.info("Loading configuration from file: {}, type: {}", configFilePath, configType);
            onConfigChange(content, configType);

        } catch (IOException e) {
            log.error("Failed to read configuration file: {}", configFilePath, e);
        }
    }

    /**
     * Detect configuration type from file extension
     */
    private String detectConfigType(String filePath) {
        String lowerPath = filePath.toLowerCase();
        if (lowerPath.endsWith(".yaml") || lowerPath.endsWith(".yml")) {
            return "YAML";
        } else if (lowerPath.endsWith(".properties")) {
            return "PROPERTIES";
        } else if (lowerPath.endsWith(".json")) {
            return "JSON";
        }
        return "YAML"; // Default to YAML
    }
}
