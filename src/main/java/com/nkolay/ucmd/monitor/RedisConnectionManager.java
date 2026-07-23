package com.nkolay.ucmd.monitor;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Her Redis instance'ı için ayrı bağlantı tutar (host:port bazlı).
 * Bağlantılar önbelleğe alınır — her istekte yeni bağlantı açılmaz.
 */
@Component
public class RedisConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(RedisConnectionManager.class);

    // "host:port" -> bağlantı
    private final Map<String, StatefulRedisConnection<byte[], byte[]>> connections =
            new ConcurrentHashMap<>();
    private final Map<String, RedisClient> clients = new ConcurrentHashMap<>();

    /**
     * Verilen host/port için bağlantı döner (yoksa kurar).
     */
    public StatefulRedisConnection<byte[], byte[]> getConnection(String host, int port) {
        String key = host + ":" + port;

        return connections.compute(key, (k, existing) -> {
            if (existing != null && existing.isOpen()) {
                return existing;
            }
            // Yok veya kapalı → yeni kur
            try {
                RedisURI uri = RedisURI.Builder.redis(host, port)
                        .withTimeout(Duration.ofSeconds(5))
                        .build();
                RedisClient client = RedisClient.create(uri);
                clients.put(k, client);
                StatefulRedisConnection<byte[], byte[]> conn =
                        client.connect(ByteArrayCodec.INSTANCE);
                logger.info("Redis bağlantısı kuruldu: {}", k);
                return conn;
            } catch (Exception e) {
                logger.error("Redis bağlantısı kurulamadı ({}): {}", k, e.getMessage());
                return null;
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Redis bağlantıları kapatılıyor ({} adet)...", connections.size());
        connections.values().forEach(c -> {
            try { if (c != null) c.close(); } catch (Exception ignored) {}
        });
        clients.values().forEach(c -> {
            try { c.shutdown(); } catch (Exception ignored) {}
        });
        connections.clear();
        clients.clear();
    }
}