package com.cuenti.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables @Async (login-triggered asset price refresh) and @Scheduled
 * (hourly price update in AssetService, which was previously never
 * triggered because scheduling was not enabled).
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    /**
     * Single-thread executor for asset price fetching: the Yahoo client
     * sleeps between requests, which must never block the shared pool.
     */
    @org.springframework.context.annotation.Bean(name = "priceExecutor")
    public java.util.concurrent.Executor priceExecutor() {
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor =
                new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("price-");
        executor.initialize();
        return executor;
    }
}
