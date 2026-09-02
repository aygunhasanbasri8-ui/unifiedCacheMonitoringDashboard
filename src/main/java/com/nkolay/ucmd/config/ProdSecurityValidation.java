package com.nkolay.ucmd.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**

 * Eksik yapılandırmayla ayağa kalkmak yerine açılışta patlar.
 */
@Configuration
@Profile("prod")
public class ProdSecurityValidation {

    public ProdSecurityValidation(
            @Value("${spring.data.redis.ssl:false}") boolean redisTls,
            @Value("${hazelcast.tls-enabled:false}") boolean hzTls,
            @Value("${spring.data.redis.password:}") String redisPassword) {

        if (!redisTls) throw new IllegalStateException(
                "Prod profilinde Redis TLS zorunludur (REDIS_TLS_ENABLED)");
        if (!hzTls) throw new IllegalStateException(
                "Prod profilinde Hazelcast TLS zorunludur (HAZELCAST_TLS_ENABLED)");
        if (redisPassword == null || redisPassword.isBlank()) throw new IllegalStateException(
                "Prod profilinde Redis parolası zorunludur (SPRING_DATA_REDIS_PASSWORD)");
    }
}
