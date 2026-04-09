package com.dynamic.thread.starter.nacos.listener;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.listener.Listener;
import com.dynamic.thread.spring.properties.DynamicThreadPoolProperties;
import com.dynamic.thread.starter.common.listener.AbstractConfigChangeListener;
import com.dynamic.thread.starter.common.refresher.ThreadPoolRefresher;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Nacos Cloud configuration change listener.
 * Listens for configuration changes from Nacos and triggers thread pool refresh.
 */
@Slf4j
public class NacosCloudConfigChangeListener extends AbstractConfigChangeListener {

    private final NacosConfigManager nacosConfigManager;
    private final DynamicThreadPoolProperties properties;
    private final Executor executor;
    private Listener listener;

    public NacosCloudConfigChangeListener(ThreadPoolRefresher refresher,
                                          NacosConfigManager nacosConfigManager,
                                          DynamicThreadPoolProperties properties) {
        super(refresher);
        this.nacosConfigManager = nacosConfigManager;
        this.properties = properties;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "dynamic-thread-nacos-listener");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void startListening() {
        DynamicThreadPoolProperties.NacosProperties nacos = properties.getNacos();
        if (nacos == null || nacos.getDataId() == null || nacos.getDataId().trim().isEmpty()) {
            log.warn("Nacos dataId is not configured, skipping listener registration");
            return;
        }

        String dataId = nacos.getDataId();
        String group = nacos.getGroup() != null ? nacos.getGroup() : "DEFAULT_GROUP";
        String configType = properties.getConfigFileType();

        try {
            // Create and register listener
            listener = new Listener() {
                @Override
                public Executor getExecutor() {
                    return executor;
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("Received Nacos config change for dataId: {}, group: {}", dataId, group);
                    onConfigChange(configInfo, configType);
                }
            };

            nacosConfigManager.getConfigService().addListener(dataId, group, listener);
            log.info("Nacos config listener registered for dataId: {}, group: {}", dataId, group);

            // Load initial configuration
            String initialConfig = nacosConfigManager.getConfigService().getConfig(dataId, group, 5000);
            if (initialConfig != null && !initialConfig.trim().isEmpty()) {
                log.info("Loading initial configuration from Nacos");
                onConfigChange(initialConfig, configType);
            }

        } catch (Exception e) {
            log.error("Failed to register Nacos config listener", e);
        }
    }

    @Override
    public void stopListening() {
        DynamicThreadPoolProperties.NacosProperties nacos = properties.getNacos();
        if (nacos == null || listener == null) {
            return;
        }

        try {
            String dataId = nacos.getDataId();
            String group = nacos.getGroup() != null ? nacos.getGroup() : "DEFAULT_GROUP";
            nacosConfigManager.getConfigService().removeListener(dataId, group, listener);
            log.info("Nacos config listener removed for dataId: {}, group: {}", dataId, group);
        } catch (Exception e) {
            log.error("Failed to remove Nacos config listener", e);
        }
    }
}
