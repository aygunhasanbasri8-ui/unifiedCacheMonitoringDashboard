package com.nkolay.ucmd.integration;

import com.nkolay.ucmd.config.KeySecurityConfig;
import com.nkolay.ucmd.enums.MetricStatus;
import com.nkolay.ucmd.model.dto.*;
import com.nkolay.ucmd.enums.MatchMode;
import com.nkolay.ucmd.model.entity.InstanceRef;
import com.nkolay.ucmd.monitor.RedisConnectionManager;
import com.nkolay.ucmd.monitor.RedisMonitor;
import com.nkolay.ucmd.security.KeyMasker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


@Testcontainers(disabledWithoutDocker = true)
class RedisAclIntegrationTest {

    private static final String READ_USER = "ucmdmon";
    private static final String READ_PASS_ENV = "UCMD_TEST_REDIS_PASSWORD";
    private static final String LIMITED_USER = "ucmdlimited";
    private static final String LIMITED_PASS_ENV = "UCMD_TEST_REDIS_LIMITED_PASSWORD";

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    private static RedisConnectionManager connectionManager;
    private static RedisMonitor monitor;
    private static InstanceRef readInstance;
    private static InstanceRef limitedInstance;

    @BeforeAll
    static void setUp() throws Exception {
        String readPass = System.getenv(READ_PASS_ENV);
        String limitedPass = System.getenv(LIMITED_PASS_ENV);
        assumeTrue(readPass != null && limitedPass != null,
                READ_PASS_ENV + " / " + LIMITED_PASS_ENV + " tanımlı değil. "
                        + "pom.xml surefire bloğu veya IntelliJ run configuration'a ekle.");

        connectionManager = new RedisConnectionManager();
        monitor = new RedisMonitor(connectionManager, new KeySecurityConfig(), new KeyMasker(true));
        ReflectionTestUtils.setField(monitor, "minPrefixLength", 3);

        REDIS.execInContainer("redis-cli", "MSET",
                "ucmd:merchant:88123:commission", "1.75",
                "ucmd:merchant:88124:commission", "2.10");

        // Salt-okunur izleme kullanıcısı
        REDIS.execInContainer("redis-cli", "ACL", "SETUSER", READ_USER,
                "on", ">" + readPass, "~*", "+@read", "+@connection", "+info", "+memory");

        // Yalnızca bağlanabilen, SCAN yetkisi olmayan kullanıcı
        REDIS.execInContainer("redis-cli", "ACL", "SETUSER", LIMITED_USER,
                "on", ">" + limitedPass, "~*", "+@connection", "+info");

        String host = REDIS.getHost();
        int port = REDIS.getMappedPort(6379);

        readInstance = new InstanceRef(10L, "redis-acl", "REDIS", host, port, true);
        readInstance.setUsername(READ_USER);
        readInstance.setPasswordEnvVar(READ_PASS_ENV);

        limitedInstance = new InstanceRef(11L, "redis-acl-limited", "REDIS", host, port, true);
        limitedInstance.setUsername(LIMITED_USER);
        limitedInstance.setPasswordEnvVar(LIMITED_PASS_ENV);
    }

    @AfterAll
    static void tearDown() {
        if (connectionManager != null) connectionManager.shutdown();
    }

    @Test
    void aclKullanicisiylaMetriklerOkunur() {
        assertThat(monitor.isReachable(readInstance)).isTrue();
        assertThat(monitor.getHealth(readInstance).getStatus()).isEqualTo("UP");

        DataStats stats = monitor.getDataStats(readInstance);
        assertThat(stats.getOverallStatus()).isEqualTo(MetricStatus.AVAILABLE);
        assertThat(stats.getTotalKeyCount()).isEqualTo(2);
    }

    @Test
    void aclKullanicisiScanYapabilir() {
        PageInfo<KeyInfo> page = monitor.scanKeys(
                readInstance, "ucmd:merchant", "0", 100, MatchMode.STARTS_WITH, null);

        assertThat(page.getStatus()).isEqualTo(MetricStatus.AVAILABLE);
        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    void yetkisizKomut_permissionDeniedDonerUnavailableDegil() {
        PageInfo<KeyInfo> page = monitor.scanKeys(
                limitedInstance, "ucmd:merchant", "0", 100, MatchMode.STARTS_WITH, null);

        assertThat(page.getStatus()).isEqualTo(MetricStatus.PERMISSION_DENIED);
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void kisitliKullaniciyaIzinliKomutlarCalismaya_devamEder() {
        assertThat(monitor.isReachable(limitedInstance)).isTrue();
        assertThat(monitor.getHealth(limitedInstance).getStatus()).isEqualTo("UP");
    }
}