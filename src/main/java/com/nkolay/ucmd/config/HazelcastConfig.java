package com.nkolay.ucmd.config;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientConnectionStrategyConfig;
import com.hazelcast.core.HazelcastInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class HazelcastConfig {

    private static final Logger logger =
            LoggerFactory.getLogger(HazelcastConfig.class);

    @Value("${hazelcast.cluster-name:dev}")
    private String clusterName;

    @Value("${hazelcast.members:localhost:5701}")
    private String members;

    @Bean(destroyMethod = "shutdown")
    public HazelcastInstance hazelcastClient() {
        ClientConfig config = new ClientConfig();

        // Cluster adı
        config.setClusterName(clusterName);
        logger.info("Connecting to Hazelcast cluster: {}", clusterName);

        // Member adresleri
        String[] memberList = members.split(",");
        for (String member : memberList) {
            config.getNetworkConfig().addAddress(member.trim());
            logger.info("Added Hazelcast member: {}", member.trim());
        }

        // Network timeout
        config.getNetworkConfig()
                .setConnectionTimeout(5000);

        // AsyncStart → uygulama Hazelcast olmasa da ayağa kalkar
        config.getConnectionStrategyConfig()
                .setAsyncStart(true)
                .setReconnectMode(
                        ClientConnectionStrategyConfig.ReconnectMode.ASYNC
                );

        // Retry ayarları
        config.getConnectionStrategyConfig()
                .getConnectionRetryConfig()
                .setClusterConnectTimeoutMillis(5000)
                .setInitialBackoffMillis(1000)
                .setMaxBackoffMillis(30000)
                .setMultiplier(1.5);

        logger.info("Hazelcast client configuration complete");
        return HazelcastClient.newHazelcastClient(config);
    }
}