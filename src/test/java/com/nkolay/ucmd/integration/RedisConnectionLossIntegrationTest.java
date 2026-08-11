package com.nkolay.ucmd.integration;

import com.nkolay.ucmd.config.KeySecurityConfig;
import com.nkolay.ucmd.model.dto.*;
import com.nkolay.ucmd.model.entity.InstanceRef;
import com.nkolay.ucmd.monitor.RedisConnectionManager;
import com.nkolay.ucmd.monitor.RedisMonitor;
import com.nkolay.ucmd.security.KeyMasker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;


@Testcontainers(disabledWithoutDocker = true)
class RedisConnectionLossIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    private static RedisConnectionManager connectionManager;

    @AfterAll
    static void tearDown() {
        if (connectionManager != null) connectionManager.shutdown();
    }

    @Test
    void baglantiKopunca_exceptionYerineUnavailableStatu() {
        connectionManager = new RedisConnectionManager();
        RedisMonitor monitor = new RedisMonitor(
                connectionManager, new KeySecurityConfig(), new KeyMasker(true));
        ReflectionTestUtils.setField(monitor, "minPrefixLength", 3);

        InstanceRef instance = new InstanceRef(20L, "redis-flaky", "REDIS",
                REDIS.getHost(), REDIS.getMappedPort(6379), true);

        // Faz 1 — ayakta
        assertThat(monitor.isReachable(instance)).isTrue();
        assertThat(monitor.getHealth(instance).getStatus()).isEqualTo("UP");

        // Faz 2 — sunucu düşüyor
        REDIS.stop();

        HealthInfo health = monitor.getHealth(instance);
        assertThat(health.getStatus()).isEqualTo("DOWN");
        assertThat(health.getVersion()).isEqualTo("unknown");

        PageInfo<KeyInfo> page = monitor.scanKeys(
                instance, "ucmd:merchant", "0", 10, MatchMode.STARTS_WITH, null);
        assertThat(page.getStatus()).isEqualTo(MetricStatus.UNAVAILABLE);
        assertThat(page.getContent()).isEmpty();
    }
}