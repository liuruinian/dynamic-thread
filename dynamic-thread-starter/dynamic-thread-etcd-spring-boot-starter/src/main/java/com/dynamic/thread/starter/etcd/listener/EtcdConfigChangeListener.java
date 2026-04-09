package com.dynamic.thread.starter.etcd.listener;

import com.dynamic.thread.spring.properties.DynamicThreadPoolProperties;
import com.dynamic.thread.starter.common.listener.AbstractConfigChangeListener;
import com.dynamic.thread.starter.common.refresher.ThreadPoolRefresher;
import io.etcd.jetcd.*;
import io.etcd.jetcd.options.WatchOption;
import io.etcd.jetcd.watch.WatchEvent;
import io.etcd.jetcd.watch.WatchResponse;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ETCD configuration change listener.
 * Uses jetcd Watch API to monitor key changes and triggers thread pool refresh.
 */
@Slf4j
public class EtcdConfigChangeListener extends AbstractConfigChangeListener {

    private final DynamicThreadPoolProperties properties;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Client etcdClient;
    private Watch.Watcher watcher;

    public EtcdConfigChangeListener(ThreadPoolRefresher refresher,
                                    DynamicThreadPoolProperties properties) {
        super(refresher);
        this.properties = properties;
    }

    @Override
    public void startListening() {
        DynamicThreadPoolProperties.EtcdProperties etcdProps = properties.getEtcd();
        if (etcdProps == null || etcdProps.getEndpoints() == null || etcdProps.getEndpoints().trim().isEmpty()) {
            log.warn("ETCD endpoints not configured, skipping ETCD listener registration");
            return;
        }

        try {
            // Parse endpoints
            String[] endpoints = etcdProps.getEndpoints().split(",");
            
            // Build client
            ClientBuilder clientBuilder = Client.builder()
                    .endpoints(Arrays.stream(endpoints)
                            .map(String::trim)
                            .toArray(String[]::new));

            // Add authentication if configured
            if (etcdProps.getUsername() != null && !etcdProps.getUsername().trim().isEmpty() 
                    && etcdProps.getPassword() != null) {
                clientBuilder.user(ByteSequence.from(etcdProps.getUsername(), StandardCharsets.UTF_8))
                        .password(ByteSequence.from(etcdProps.getPassword(), StandardCharsets.UTF_8));
            }

            etcdClient = clientBuilder.build();
            
            String keyPrefix = etcdProps.getKeyPrefix();
            log.info("ETCD config listener starting for key prefix: {}", keyPrefix);

            // Load initial configuration
            loadConfiguration(keyPrefix);

            // Start watching for changes
            startWatching(keyPrefix);

            running.set(true);
            log.info("ETCD config listener started successfully");

        } catch (Exception e) {
            log.error("Failed to start ETCD listener", e);
        }
    }

    @Override
    public void stopListening() {
        running.set(false);

        if (watcher != null) {
            try {
                watcher.close();
                watcher = null;
                log.info("ETCD watcher closed");
            } catch (Exception e) {
                log.error("Failed to close ETCD watcher", e);
            }
        }

        if (etcdClient != null) {
            try {
                etcdClient.close();
                etcdClient = null;
                log.info("ETCD client closed");
            } catch (Exception e) {
                log.error("Failed to close ETCD client", e);
            }
        }

        log.info("ETCD config listener stopped");
    }

    /**
     * Load configuration from ETCD
     */
    private void loadConfiguration(String keyPrefix) {
        try {
            KV kvClient = etcdClient.getKVClient();
            ByteSequence key = ByteSequence.from(keyPrefix, StandardCharsets.UTF_8);

            CompletableFuture<io.etcd.jetcd.kv.GetResponse> future = kvClient.get(key);
            io.etcd.jetcd.kv.GetResponse response = future.get();

            if (response.getKvs().isEmpty()) {
                log.debug("No configuration found at key: {}", keyPrefix);
                return;
            }

            KeyValue kv = response.getKvs().get(0);
            String configContent = kv.getValue().toString(StandardCharsets.UTF_8);

            if (configContent == null || configContent.trim().isEmpty()) {
                log.debug("Configuration content is empty at key: {}", keyPrefix);
                return;
            }

            DynamicThreadPoolProperties.EtcdProperties etcdProps = properties.getEtcd();
            String configType = etcdProps.getConfigType() != null ? etcdProps.getConfigType() : "YAML";

            log.info("Loading configuration from ETCD, key: {}, version: {}", keyPrefix, kv.getVersion());
            onConfigChange(configContent, configType);

        } catch (Exception e) {
            log.error("Failed to load configuration from ETCD", e);
        }
    }

    /**
     * Start watching for configuration changes
     */
    private void startWatching(String keyPrefix) {
        Watch watchClient = etcdClient.getWatchClient();
        ByteSequence key = ByteSequence.from(keyPrefix, StandardCharsets.UTF_8);

        // Watch with prefix option to watch all keys under the prefix
        WatchOption watchOption = WatchOption.newBuilder()
                .isPrefix(true)
                .build();

        watcher = watchClient.watch(key, watchOption, new Watch.Listener() {
            @Override
            public void onNext(WatchResponse response) {
                for (WatchEvent event : response.getEvents()) {
                    WatchEvent.EventType eventType = event.getEventType();
                    KeyValue keyValue = event.getKeyValue();
                    String changedKey = keyValue.getKey().toString(StandardCharsets.UTF_8);

                    log.info("ETCD config changed, key: {}, event: {}", changedKey, eventType);

                    if (eventType == WatchEvent.EventType.PUT) {
                        String configContent = keyValue.getValue().toString(StandardCharsets.UTF_8);
                        if (configContent != null && !configContent.trim().isEmpty()) {
                            DynamicThreadPoolProperties.EtcdProperties etcdProps = properties.getEtcd();
                            String configType = etcdProps.getConfigType() != null 
                                    ? etcdProps.getConfigType() 
                                    : "YAML";
                            onConfigChange(configContent, configType);
                        }
                    } else if (eventType == WatchEvent.EventType.DELETE) {
                        log.warn("Configuration key deleted: {}", changedKey);
                    }
                }
            }

            @Override
            public void onError(Throwable throwable) {
                log.error("ETCD watch error", throwable);
                // Attempt to reconnect
                if (running.get()) {
                    log.info("Attempting to reconnect ETCD watcher...");
                    try {
                        Thread.sleep(5000);
                        if (running.get()) {
                            startWatching(keyPrefix);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            @Override
            public void onCompleted() {
                log.info("ETCD watch completed");
            }
        });

        log.info("ETCD watcher started for key prefix: {}", keyPrefix);
    }
}
