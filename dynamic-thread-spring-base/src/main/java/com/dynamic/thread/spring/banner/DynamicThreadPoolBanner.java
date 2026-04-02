package com.dynamic.thread.spring.banner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

/**
 * Banner printer for Dynamic Thread Pool framework.
 * Prints a startup banner when the application is ready.
 */
@Slf4j
public class DynamicThreadPoolBanner implements ApplicationListener<ApplicationReadyEvent> {

    private static final String BANNER = 
            "\n" +
            " ____                              _        _____ _                        _   ____             _\n" +
            "|  _ \\  _   _  _ __    __ _  _ __ ___ (_)  ___  |_   _|| |__   _ __  ___   __ _  __| ||  _ \\  ___    ___  | |\n" +
            "| | | || | | || '_ \\  / _` || '_ ` _ \\| | / __|   | |  | '_ \\ | '__|/ _ \\ / _` |/ _` ||  _ \\ / _ \\  / _ \\ | |\n" +
            "| |_| || |_| || | | || (_| || | | | | | || (__    | |  | | | || |  |  __/| (_| || (_| || |_) || (_) || (_) || |\n" +
            "|____/  \\__, ||_| |_| \\__,_||_| |_| |_|_| \\___|   |_|  |_| |_||_|   \\___| \\__,_| \\__,_||  __/  \\___/  \\___/ |_|\n" +
            "        |___/                                                                         |_|\n" +
            "\n" +
            ":: Dynamic Thread Pool ::                                            (v1.0.0)\n";

    private final boolean enabled;

    public DynamicThreadPoolBanner(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (enabled) {
            System.out.println(BANNER);
            log.info("Dynamic Thread Pool framework started successfully!");
        }
    }
}
