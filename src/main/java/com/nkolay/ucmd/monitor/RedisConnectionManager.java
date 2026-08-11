package com.nkolay.ucmd.monitor;

import com.nkolay.ucmd.model.entity.InstanceRef;
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
 * Her Redis instance'ı için ayrı bağlantı tutar (host:port:username bazlı).
 * Bağlantılar önbelleğe alınır — her istekte yeni bağlantı açılmaz.
 *
 * Parola veritabanında saklanmaz; instance yalnızca hangi environment
 * değişkeninden okunacağının ADINI taşır (rehber §9).
 */
@Component
public class RedisConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(RedisConnectionManager.class);

    // "host:port:username:tls" -> bağlantı
    private final Map<String, StatefulRedisConnection<byte[], byte[]>> connections =
            new ConcurrentHashMap<>();
    private final Map<String, RedisClient> clients = new ConcurrentHashMap<>();

    /**
     * Verilen instance için bağlantı döner (yoksa kurar).
     * Aynı sunucuya farklı kullanıcıyla bağlanma senaryosu için cache anahtarı
     * kullanıcı adını da içerir.
     */
    public StatefulRedisConnection<byte[], byte[]> getConnection(InstanceRef instance) {
        String host = instance.getHost();
        int port = instance.getPort();
        String username = instance.getUsername();
        boolean tls = instance.isTlsEnabled();
        String key = host + ":" + port + ":" + (username == null ? "" : username) + ":" + (tls ? "tls" : "plain");

        return connections.compute(key, (k, existing) -> {
            if (existing != null && existing.isOpen()) {
                return existing;
            }
            // Yok veya kapalı → yeni kur
            try {
                RedisURI.Builder builder = RedisURI.Builder.redis(host, port)
                        .withTimeout(Duration.ofSeconds(5));

                if (tls) {
                    builder.withSsl(true);
                }

                String password = resolvePassword(instance);
                if (password != null && !password.isBlank()) {
                    if (username != null && !username.isBlank()) {
                        builder.withAuthentication(username, password.toCharArray());
                    } else {
                        builder.withPassword(password.toCharArray());
                    }
                }

                RedisClient client = RedisClient.create(builder.build());
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

    /** Parolayı environment değişkeninden okur. Tanımlı değilse null. */
    private String resolvePassword(InstanceRef instance) {
        String envVar = instance.getPasswordEnvVar();
        if (envVar == null || envVar.isBlank()) return null;

        String value = System.getenv(envVar);
        if (value == null || value.isBlank()) {
            logger.warn("Instance '{}' için parola env değişkeni '{}' tanımlı değil",
                    instance.getName(), envVar);
        }
        return value;
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
