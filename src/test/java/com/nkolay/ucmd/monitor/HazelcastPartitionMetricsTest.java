package com.nkolay.ucmd.monitor;

import com.hazelcast.cluster.Address;
import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.partition.Partition;
import com.hazelcast.partition.PartitionService;
import com.nkolay.ucmd.config.KeySecurityConfig;
import com.nkolay.ucmd.model.dto.ClusterInfo;
import com.nkolay.ucmd.enums.MetricStatus;
import com.nkolay.ucmd.security.KeyMasker;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Rehber §5.4 — gerçek partition sayısı, dağılım ve skew hesabı. */
class HazelcastPartitionMetricsTest {

    private final HazelcastMonitor monitor =
            new HazelcastMonitor(null, null, new KeyMasker(true), new KeySecurityConfig());

    private void apply(HazelcastInstance client, ClusterInfo info) {
        ReflectionTestUtils.invokeMethod(monitor, "applyPartitionMetrics", client, info);
    }

    private static Member member(String host, int port) throws UnknownHostException {
        Member m = mock(Member.class);
        when(m.getAddress()).thenReturn(new Address(host, port));
        return m;
    }

    private static Set<Partition> partitions(Map<Member, Integer> owned, int unassigned) {
        Set<Partition> set = new LinkedHashSet<>();
        owned.forEach((m, count) -> {
            for (int i = 0; i < count; i++) {
                Partition p = mock(Partition.class);
                when(p.getOwner()).thenReturn(m);
                set.add(p);
            }
        });
        for (int i = 0; i < unassigned; i++) {
            Partition p = mock(Partition.class);
            when(p.getOwner()).thenReturn(null);
            set.add(p);
        }
        return set;
    }

    private static HazelcastInstance clientWith(Set<Partition> partitions) {
        HazelcastInstance client = mock(HazelcastInstance.class);
        PartitionService ps = mock(PartitionService.class);
        when(client.getPartitionService()).thenReturn(ps);
        when(ps.getPartitions()).thenReturn(partitions);
        return client;
    }

    @Test
    void dengeliDagilim_271PartitionIkiUye() throws Exception {
        Map<Member, Integer> owned = new LinkedHashMap<>();
        owned.put(member("127.0.0.1", 5701), 136);
        owned.put(member("127.0.0.1", 5702), 135);

        ClusterInfo info = new ClusterInfo();
        apply(clientWith(partitions(owned, 0)), info);

        assertThat(info.getPartitionCount()).isEqualTo(271);
        assertThat(info.getPartitionCountStatus()).isEqualTo(MetricStatus.AVAILABLE);
        assertThat(info.getPartitionDistribution())
                .containsEntry("127.0.0.1:5701", 136)
                .containsEntry("127.0.0.1:5702", 135);
        assertThat(info.getUnassignedPartitions()).isZero();
        assertThat(info.getDistributionSkewPercent()).isEqualTo(0.74);   // (1 / 135.5) * 100
        assertThat(info.getBalanced()).isTrue();
        assertThat(info.getDistributionStatus()).isEqualTo(MetricStatus.AVAILABLE);
        assertThat(info.getBalancedStatus()).isEqualTo(MetricStatus.AVAILABLE);
    }

    @Test
    void carpikDagilim_balancedFalse() throws Exception {
        Map<Member, Integer> owned = new LinkedHashMap<>();
        owned.put(member("127.0.0.1", 5701), 90);
        owned.put(member("127.0.0.1", 5702), 10);

        ClusterInfo info = new ClusterInfo();
        apply(clientWith(partitions(owned, 0)), info);

        assertThat(info.getPartitionCount()).isEqualTo(100);
        assertThat(info.getDistributionSkewPercent()).isEqualTo(160.0);  // (80 / 50) * 100
        assertThat(info.getBalanced()).isFalse();
        assertThat(info.getDistributionStatus()).isEqualTo(MetricStatus.AVAILABLE);
    }

    @Test
    void tekUye_skewSifir() throws Exception {
        Map<Member, Integer> owned = new LinkedHashMap<>();
        owned.put(member("127.0.0.1", 5701), 271);

        ClusterInfo info = new ClusterInfo();
        apply(clientWith(partitions(owned, 0)), info);

        assertThat(info.getPartitionDistribution()).hasSize(1);
        assertThat(info.getDistributionSkewPercent()).isEqualTo(0.0);
        assertThat(info.getBalanced()).isTrue();
    }

    @Test
    void sahipsizPartitionVarsa_estimatedVeBalancedFalse() throws Exception {
        Map<Member, Integer> owned = new LinkedHashMap<>();
        owned.put(member("127.0.0.1", 5701), 50);
        owned.put(member("127.0.0.1", 5702), 50);

        ClusterInfo info = new ClusterInfo();
        apply(clientWith(partitions(owned, 5)), info);

        assertThat(info.getPartitionCount()).isEqualTo(105);
        assertThat(info.getUnassignedPartitions()).isEqualTo(5);
        assertThat(info.getBalanced()).isFalse();
        assertThat(info.getDistributionStatus()).isEqualTo(MetricStatus.ESTIMATED);
    }

    @Test
    void partitionTablosuBos_unavailable() {
        ClusterInfo info = new ClusterInfo();
        apply(clientWith(Set.of()), info);

        assertThat(info.getPartitionCountStatus()).isEqualTo(MetricStatus.UNAVAILABLE);
        assertThat(info.getDistributionStatus()).isEqualTo(MetricStatus.UNAVAILABLE);
    }

    @Test
    void clientDesteklemiyorsa_notApplicable() {
        HazelcastInstance client = mock(HazelcastInstance.class);
        when(client.getPartitionService()).thenThrow(new UnsupportedOperationException());

        ClusterInfo info = new ClusterInfo();
        apply(client, info);

        assertThat(info.getPartitionCountStatus()).isEqualTo(MetricStatus.NOT_APPLICABLE);
        assertThat(info.getDistributionStatus()).isEqualTo(MetricStatus.NOT_APPLICABLE);
        assertThat(info.getBalanced()).isNull();
    }

    @Test
    void beklenmedikHata_unavailable() {
        HazelcastInstance client = mock(HazelcastInstance.class);
        when(client.getPartitionService()).thenThrow(new IllegalStateException("client not connected"));

        ClusterInfo info = new ClusterInfo();
        apply(client, info);

        assertThat(info.getPartitionCountStatus()).isEqualTo(MetricStatus.UNAVAILABLE);
        assertThat(info.getDistributionStatus()).isEqualTo(MetricStatus.UNAVAILABLE);
        assertThat(info.getBalanced()).isNull();
    }
}