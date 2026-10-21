package com.ucmd.integration;


import com.ucmd.config.KeySecurityConfig;
import com.ucmd.enums.MetricStatus;
import com.ucmd.enums.TtlStatus;
import com.ucmd.enums.MatchMode;
import com.ucmd.exception.InvalidPrefixException;
import com.ucmd.model.dto.*;
import com.ucmd.model.entity.InstanceRef;
import com.ucmd.monitor.RedisConnectionManager;
import com.ucmd.monitor.RedisMonitor;
import com.ucmd.security.KeyMasker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
public class RedisStandaloneIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    private static RedisConnectionManager connectionManager;
    private static RedisMonitor monitor;
    private static InstanceRef instance;

    @BeforeAll
    static void setUp() throws Exception {
        connectionManager = new RedisConnectionManager();
        monitor = new RedisMonitor(connectionManager, new KeySecurityConfig(), new KeyMasker(true));
        ReflectionTestUtils.setField(monitor, "minPrefixLength", 3);

        instance = new InstanceRef(1L, "redis-test", InstanceType.REDIS, REDIS.getHost(), REDIS.getMappedPort(6379), true, false);

        // Test fixture'ı — UCMD kodu salt-okunur kalır, veriyi container'a redis-cli yazar.
        redisCli("redis-cli", "MSET",
                "ucmd:merchant:88123:commission", "1.75",
                "ucmd:merchant:88124:commission", "2.10",
                "ucmd:terminal:5501:status", "ACTIVE",
                "other:key", "x");
        redisCli("redis-cli", "EXPIRE", "ucmd:terminal:5501:status", "600");
        redisCli("redis-cli", "GET", "other:key");      // keyspace_hits += 1
        redisCli("redis-cli", "GET", "olmayan:key");    // keyspace_misses += 1
    }

    /** Fixture komutu sessizce başarısız olmasın; çıkış kodu 0 değilse testi patlat. */
    private static void redisCli(String... args) throws Exception {
        var result = REDIS.execInContainer(args);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException(
                    "redis-cli başarısız (" + result.getExitCode() + "): " + result.getStderr());
        }
    }

    @AfterAll
    static void tearDown() {
        connectionManager.shutdown();
    }

    @Test
    void erisilebilirlik() {
        assertThat(monitor.isReachable(instance)).isTrue();
    }

    @Test
    void health_upVeVersiyonDoner() {
        HealthInfo health = monitor.getHealth(instance);

        assertThat(health.getStatus()).isEqualTo("UP");
        assertThat(health.getVersion()).isNotEqualTo("unknown");
        assertThat(health.getUptimeSeconds()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void resourceStats_bellekOkunurJvmAlanlariNotApplicable() {
        ResourceStats stats = monitor.getResourceStats(instance);

        assertThat(stats.getOverallStatus()).isEqualTo(MetricStatus.AVAILABLE);
        assertThat(stats.getMemoryStatus()).isEqualTo(MetricStatus.AVAILABLE);
        assertThat(stats.getFragmentationStatus()).isEqualTo(MetricStatus.AVAILABLE);
        assertThat(stats.getMemoryUsedBytes()).isGreaterThan(0);

        assertThat(stats.getJvmStatus()).isEqualTo(MetricStatus.NOT_APPLICABLE);
        assertThat(stats.getGcStatus()).isEqualTo(MetricStatus.NOT_APPLICABLE);
        assertThat(stats.getThreadStatus()).isEqualTo(MetricStatus.NOT_APPLICABLE);
        assertThat(stats.getFileDescriptorStatus()).isEqualTo(MetricStatus.NOT_APPLICABLE);
    }


    @Test
    void dataStats_keyspaceOkunurTtlEstimated() {
        DataStats stats = monitor.getDataStats(instance);

        assertThat(stats.getOverallStatus()).isEqualTo(MetricStatus.AVAILABLE);
        assertThat(stats.getTotalKeyCount()).isEqualTo(4);
        assertThat(stats.getKeysWithExpiry()).isEqualTo(1);
        assertThat(stats.getKeysPerDatabase()).containsKey("db0");
        assertThat(stats.getTtlStatus()).isEqualTo(MetricStatus.ESTIMATED);
        assertThat(stats.getEvictionStatus()).isEqualTo(MetricStatus.AVAILABLE);


    }

    @Test
    void cacheEfficicencyStats_hitVeMissSonrasiHesaplanir() {
        CacheEfficiencyStats stats = monitor.getCacheEfficiency(instance);

        assertThat(stats.getHitRatio()).isPositive().isBetween(0.0, 1.0);
        assertThat(stats.getOverallStatus()).isEqualTo(MetricStatus.AVAILABLE);
    }

    @Test
    void scanKeys_prefixEslesenAnahtarlarMaskeliDoner() {
        PageInfo<KeyInfo> page = monitor.scanKeys(instance,"ucmd:merchant", "0",100, MatchMode.STARTS_WITH, null );

        assertThat(page.getStatus()).isEqualTo(MetricStatus.AVAILABLE);
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent()).allSatisfy(k -> assertThat(k.getKeyName()).contains("***"));
        assertThat(page.getContent()).extracting(KeyInfo::getKeyName).containsOnly("ucmd:merchant:***:commission");

    }

    @Test
    void scanKeys_kısaPrefixReddedilir() {
        assertThatThrownBy(() -> monitor.scanKeys(instance, "uc", "0", 10, MatchMode.STARTS_WITH, null)).isInstanceOf(InvalidPrefixException.class);

    }

    @Test
    void scanKeys_bosPrefixReddedilir() {
        assertThatThrownBy(() -> monitor.scanKeys(instance, "  ", "0", 10, MatchMode.STARTS_WITH, null)).isInstanceOf(InvalidPrefixException.class);

    }

    @Test
    void scanKeys_baslangictaJokerKarakterReddedilir() {
        assertThatThrownBy(() -> monitor.scanKeys(instance, "*commission", "0", 10, MatchMode.STARTS_WITH, null)).isInstanceOf(InvalidPrefixException.class);
    }

    @Test
    void scanKeys_jokerKarakterReddedilir() {
        assertThatThrownBy(() -> monitor.scanKeys(instance, "***", "0", 10, MatchMode.STARTS_WITH,null)).isInstanceOf(InvalidPrefixException.class);

    }

    @Test
    void keyTtlResponse_expireTanimliVeTanimsizKey() {
        KeyTtlResponse expiring = monitor.getKeyTTL(instance, "ucmd:terminal:5501:status", null);
        assertThat(expiring.getTtlStatus()).isEqualTo(TtlStatus.EXPIRING);
        assertThat(expiring.getTtlMillis()).isPositive();

        KeyTtlResponse persistent = monitor.getKeyTTL(instance, "other:key", null);
        assertThat(persistent.getTtlStatus()).isEqualTo(TtlStatus.PERSISTENT);

        KeyTtlResponse missing = monitor.getKeyTTL(instance, "olmayan:key", null);
        assertThat(missing.getTtlStatus()).isEqualTo(TtlStatus.NOT_FOUND);

    }

    @Test
    void topNKeys_boyutaGoreSiralar() {
        TopKeysResponse top = monitor.getTopNKeys(instance, 3, 1000);

        assertThat(top.getStatus())
                .isIn(MetricStatus.AVAILABLE, MetricStatus.ESTIMATED);
        assertThat(top.getKeys()).isNotEmpty().hasSizeLessThanOrEqualTo(3);
    }

    @Test
    void clusterInfo_standaloneIcinNotApplicable() {
        ClusterInfo info = monitor.getClusterInfo(instance);

        assertThat(info.getOverallStatus()).isEqualTo(MetricStatus.NOT_APPLICABLE);
    }

    @Test
    void mapVeOperationStats_redisIcinNotApplicable() {
        assertThat(monitor.getMapStats(instance).getOverallStatus())
                .isEqualTo(MetricStatus.NOT_APPLICABLE);
        assertThat(monitor.getOperationStats(instance).getOverallStatus())
                .isEqualTo(MetricStatus.NOT_APPLICABLE);
    }


}
