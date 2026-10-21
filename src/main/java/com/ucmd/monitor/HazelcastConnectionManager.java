package com.ucmd.monitor;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientConnectionStrategyConfig;
import com.hazelcast.core.HazelcastInstance;
import com.ucmd.model.entity.InstanceRef;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class HazelcastConnectionManager {

    private static final Logger logger =
            LoggerFactory.getLogger(HazelcastConnectionManager.class);

    // "clusterName|members" -> client
    private final Map<String, HazelcastInstance> clients = new ConcurrentHashMap<>();

    public HazelcastInstance getClient(InstanceRef instance) {
        String clusterName = instance.getClusterName();
        if (clusterName == null || clusterName.isBlank()) {
            // Yapılandırma hatası gizlenmemeli → 400
            throw new IllegalArgumentException(
                    "Hazelcast instance '" + instance.getName() + "' için cluster-name tanımlı değil");
        }

        String key = instance.connectionKey();

        return clients.compute(key, (k, existing) -> {
            if (existing != null) {
                try {
                    if (existing.getLifecycleService().isRunning()) {
                        return existing;
                    }
                } catch (Exception ignored) { }
                // Ölü client → cache'ten çıkar, yeniden kur
                try { existing.shutdown(); } catch (Exception ignored) { }
            }

            try {
                ClientConfig config = new ClientConfig();
                config.setClusterName(clusterName);

                // Unisocket — host, container DNS'ini çözemiyor
                config.getNetworkConfig().setSmartRouting(false);

                for (String member : instance.resolveMembers().split(",")) {
                    String trimmed = member.trim();
                    if (!trimmed.isEmpty()) {
                        config.getNetworkConfig().addAddress(trimmed);
                    }
                }

                config.getNetworkConfig().setConnectionTimeout(5000);

                // AsyncStart → Hazelcast kapalıyken de uygulama ayağa kalkar
                config.getConnectionStrategyConfig()
                        .setAsyncStart(true)
                        .setReconnectMode(
                                ClientConnectionStrategyConfig.ReconnectMode.ASYNC);

                config.getConnectionStrategyConfig()
                        .getConnectionRetryConfig()
                        .setClusterConnectTimeoutMillis(5000)
                        .setInitialBackoffMillis(1000)
                        .setMaxBackoffMillis(30000)
                        .setMultiplier(1.5);

                HazelcastInstance client = HazelcastClient.newHazelcastClient(config);
                logger.info("Hazelcast client kuruldu: cluster '{}', members '{}'",
                        clusterName, instance.resolveMembers());
                return client;

            } catch (Exception e) {
                logger.error("Hazelcast client kurulamadı ({}): {}", k, e.getMessage());
                return null;
            }
        });
    }

    public void evict(InstanceRef instance) {
        String key = instance.connectionKey();
        HazelcastInstance removed = clients.remove(key);
        if (removed != null) {
            try {
                removed.shutdown();
                logger.info("Hazelcast client kapatıldı: {}", key);
            } catch (Exception e) {
                logger.warn("Hazelcast client kapatılamadı ({}): {}", key, e.getMessage());
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Hazelcast client'ları kapatılıyor ({} adet)...", clients.size());
        clients.values().forEach(c -> {
            try { if (c != null) c.shutdown(); } catch (Exception ignored) { }
        });
        clients.clear();
    }
}
