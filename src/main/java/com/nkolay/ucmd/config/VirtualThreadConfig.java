package com.nkolay.ucmd.config;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Configuration(proxyBeanMethods = false)
public class VirtualThreadConfig {

    private static final Logger logger =
            LoggerFactory.getLogger(VirtualThreadConfig.class);

    private ExecutorService virtualThreadExecutor;

    @Bean
    public ExecutorService virtualThreadExecutor() {
        virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        logger.info("Virtual thread executor initialized");
        return virtualThreadExecutor;
    }

    @PreDestroy
    public void shutdown() {
        if (virtualThreadExecutor != null && !virtualThreadExecutor.isShutdown()) {
            logger.info("Shutting down virtual thread executor...");
            virtualThreadExecutor.shutdown();
            try {
                // Aktif thread'lerin bitmesi için 10 sn bekle
                if (!virtualThreadExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.warn("Virtual thread executor did not terminate in time, forcing shutdown");
                    virtualThreadExecutor.shutdownNow();
                }
                logger.info("Virtual thread executor shutdown complete");
            } catch (InterruptedException e) {
                logger.error("Interrupted while waiting for executor shutdown");
                virtualThreadExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}