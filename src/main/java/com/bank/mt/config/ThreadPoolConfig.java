package com.bank.mt.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class ThreadPoolConfig {

    @Bean("ingestionExecutor")
    public Executor ingestionExecutor(
            @Value("${mt.thread-pool.ingestion.core-size:4}") int coreSize,
            @Value("${mt.thread-pool.ingestion.max-size:8}") int maxSize,
            @Value("${mt.thread-pool.ingestion.queue-capacity:100}") int queueCapacity) {
        return buildExecutor("ingestion-", coreSize, maxSize, queueCapacity);
    }

    @Bean("aggregationExecutor")
    public Executor aggregationExecutor(
            @Value("${mt.thread-pool.aggregation.core-size:2}") int coreSize,
            @Value("${mt.thread-pool.aggregation.max-size:4}") int maxSize,
            @Value("${mt.thread-pool.aggregation.queue-capacity:50}") int queueCapacity) {
        return buildExecutor("aggregation-", coreSize, maxSize, queueCapacity);
    }

    @Bean("deliveryExecutor")
    public Executor deliveryExecutor(
            @Value("${mt.thread-pool.delivery.core-size:4}") int coreSize,
            @Value("${mt.thread-pool.delivery.max-size:8}") int maxSize,
            @Value("${mt.thread-pool.delivery.queue-capacity:200}") int queueCapacity) {
        return buildExecutor("delivery-", coreSize, maxSize, queueCapacity);
    }

    private ThreadPoolTaskExecutor buildExecutor(String prefix, int coreSize, int maxSize, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(prefix);
        executor.initialize();
        return executor;
    }
}
