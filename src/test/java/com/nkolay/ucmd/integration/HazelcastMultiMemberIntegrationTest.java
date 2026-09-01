package com.nkolay.ucmd.integration;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.nkolay.ucmd.config.KeySecurityConfig;
import com.nkolay.ucmd.enums.MatchMode;
import com.nkolay.ucmd.enums.MetricStatus;
import com.nkolay.ucmd.enums.TtlStatus;
import com.nkolay.ucmd.model.dto.*;
import com.nkolay.ucmd.model.entity.InstanceRef;
import com.nkolay.ucmd.monitor.HazelcastConnectionManager;
import com.nkolay.ucmd.monitor.HazelcastMetricsClient;
import com.nkolay.ucmd.monitor.HazelcastMonitor;
import com.nkolay.ucmd.security.KeyMasker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;


class HazelcastMultiMemberIntegrationTest {

    private static final int PORT_1 = 5801;
    private static final int PORT_2 = 5802;
    private static final String MAP_NAME = "ucmd-test-map";

    private static String clusterName;
    private static HazelcastInstance member1;
    private static HazelcastInstance member2;
    private static HazelcastConnectionManager connectionManager;
    private static HazelcastMonitor monitor;
    private static InstanceRef instance;

    private static Config memberConfig() {
        Config config = new Config();
        config.setClusterName(clusterName);
        config.setProperty("hazelcast.local.localAddress", "127.0.0.1");
        config.getJetConfig().setEnabled(false);
        config.getNetworkConfig().setPort(PORT_1).setPortAutoIncrement(true);

        JoinConfig join = config.getNetworkConfig().getJoin();
        join.getMulticastConfig().setEnabled(false);
        join.getAutoDetectionConfig().setEnabled(false);
        join.getTcpIpConfig().setEnabled(true)
                .addMember("127.0.0.1:" + PORT_1)
                .addMember("127.0.0.1:" + PORT_2);
        return config;
    }

    @BeforeAll
    static void setUp() throws Exception {
        clusterName = "ucmd-test-" + UUID.randomUUID();
        member1 = Hazelcast.newHazelcastInstance(memberConfig());
        member2 = Hazelcast.newHazelcastInstance(memberConfig());

        member1.getMap(MAP_NAME).put("merchant:88123", "1.75");
        member1.getMap(MAP_NAME).put("merchant:88124", "2.10");
        member1.getMap(MAP_NAME).put("terminal:5501", "ACTIVE");

        connectionManager = new HazelcastConnectionManager();
        monitor = new HazelcastMonitor(connectionManager, new HazelcastMetricsClient(),
                new KeyMasker(true), new KeySecurityConfig());
        ReflectionTestUtils.setField(monitor, "hazelcastMaxScanEntries", 5000);

        instance = new InstanceRef(30L, "hz-test", "HAZELCAST", "127.0.0.1", PORT_1, true);
        instance.setClusterName(clusterName);
        instance.setMembers("127.0.0.1:" + PORT_1 + ",127.0.0.1:" + PORT_2);
        // metricsUrl bilinçli olarak null — jmx_exporter yok

        waitUntilReachable();
    }

    /** Client asyncStart ile kurulur; cluster bağlantısını beklemek gerekir. */
    private static void waitUntilReachable() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (monitor.isReachable(instance)) return;
            Thread.sleep(250);
        }
        throw new IllegalStateException("Hazelcast client 20 sn içinde cluster'a bağlanamadı");
    }

    @AfterAll
    static void tearDown() {
        if (connectionManager != null) connectionManager.shutdown();
        if (member1 != null) member1.shutdown();
        if (member2 != null) member2.shutdown();
    }

    @Test
    void clusterInfo_ikiUyeVeGercekPartitionMetrikleri() {
        ClusterInfo info = monitor.getClusterInfo(instance);

        assertThat(info.getOverallStatus()).isEqualTo(MetricStatus.AVAILABLE);
        assertThat(info.getNodeCount()).isEqualTo(2);
        assertThat(info.getClusterName()).isEqualTo(clusterName);
        assertThat(info.getMembers()).hasSize(2);

        assertThat(info.getPartitionCountStatus()).isEqualTo(MetricStatus.AVAILABLE);
        assertThat(info.getPartitionCount()).isEqualTo(271);
        assertThat(info.getPartitionDistribution()).hasSize(2);
        assertThat(info.getUnassignedPartitions()).isZero();
        assertThat(info.getBalanced()).isTrue();
    }

    @Test
    void clusterSafe_clientTarafindaDesteklenmiyor() {
        // Bilinen sınır 1 — client-only'de isClusterSafe() yok
        ClusterInfo info = monitor.getClusterInfo(instance);

        assertThat(info.getSafetyStatus())
                .isIn(MetricStatus.NOT_APPLICABLE, MetricStatus.AVAILABLE);
    }

    @Test
    void mapStats_prometheusYokkenKismiYanit() {
        MapStatsInfo stats = monitor.getMapStats(instance);

        // Entry sayısı client'tan geliyor → mevcut
        assertThat(stats.getOverallStatus()).isEqualTo(MetricStatus.AVAILABLE);
        assertThat(stats.getMaps())
                .anySatisfy(m -> {
                    assertThat(m.getName()).isEqualTo(MAP_NAME);
                    assertThat(m.getEntryCount()).isEqualTo(3);
                    assertThat(m.getEntryCountStatus()).isEqualTo(MetricStatus.AVAILABLE);
                    assertThat(m.getOwnedEntryMemoryCost()).isNull();
                    assertThat(m.getOwnedEntryCount()).isNull();
                    assertThat(m.getHits()).isNull();
                });


        assertThat(stats.getMemoryCostStatus()).isEqualTo(MetricStatus.UNAVAILABLE);
        assertThat(stats.getOperationStatus()).isEqualTo(MetricStatus.UNAVAILABLE);
    }

    @Test
    void cacheEfficiency_hazelcastIcinNotApplicable() {
        // Bilinen sınır 3 — hits ve getcount farklı şeyleri sayar, oran anlamsız
        CacheEfficiencyStats stats = monitor.getCacheEfficiency(instance);

        assertThat(stats.getOverallStatus()).isEqualTo(MetricStatus.NOT_APPLICABLE);
        assertThat(stats.getHitRatioStatus()).isEqualTo(MetricStatus.NOT_APPLICABLE);
    }

    @Test
    void scanKeys_mapIcindeEntryArar() {
        PageInfo<KeyInfo> page = monitor.scanKeys(
                instance, "merchant", "0", 100, MatchMode.STARTS_WITH, MAP_NAME);

        assertThat(page.getStatus())
                .isIn(MetricStatus.AVAILABLE, MetricStatus.ESTIMATED);
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent())
                .allSatisfy(k -> assertThat(k.getKeyName()).contains("***"));
    }

    @Test
    void keyTtl_expireTanimsizEntry() {
        KeyTtlResponse ttl = monitor.getKeyTTL(instance, "merchant:88123", MAP_NAME);

        assertThat(ttl.getTtlStatus())
                .isIn(TtlStatus.PERSISTENT, TtlStatus.EXPIRING);
    }

    @Test
    void tanimsizClusterName_illegalArgument() {
        InstanceRef bozuk = new InstanceRef(31L, "hz-bozuk", "HAZELCAST", "127.0.0.1", PORT_1, true);

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> connectionManager.getClient(bozuk))
                .isInstanceOf(IllegalArgumentException.class);
    }
}