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
}
