package com.java_download_manager.jdm.config;

import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

    @Value("${jdm.download.async-pool-size:8}")
    private int asyncPoolSize;

    /**
     * Executor for parallel download task execution (used by RabbitMQ consumer and cron).
     * Bounded pool prevents unlimited threads when many tasks are queued.
     */
    @Bean(name = "downloadTaskExecutor")
    public Executor downloadTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(asyncPoolSize);
        executor.setMaxPoolSize(asyncPoolSize * 2);
        executor.setQueueCapacity(128);
        executor.setThreadNamePrefix("jdm-download-");
        executor.initialize();
        return executor;
    }
}
