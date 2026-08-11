package com.nkolay.ucmd.monitor;

import com.nkolay.ucmd.config.KeySecurityConfig;
import com.nkolay.ucmd.security.KeyMasker;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** INFO keyspace ayrıştırma —  DBSIZE yerine tüm db'ler toplanır. */
class RedisMonitorKeyspaceParserTest {

    private final RedisMonitor monitor =
            new RedisMonitor(null, new KeySecurityConfig(), new KeyMasker(true));

    private Object parse(String keyspaceInfo) {
        return ReflectionTestUtils.invokeMethod(monitor, "parseKeyspace", keyspaceInfo);
    }

    private static long totalKeys(Object s) {
        return (Long) ReflectionTestUtils.getField(s, "totalKeys");
    }

    private static long totalExpires(Object s) {
        return (Long) ReflectionTestUtils.getField(s, "totalExpires");
    }

    private static Long avgTtl(Object s) {
        return (Long) ReflectionTestUtils.getField(s, "avgTtlMillis");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Long> perDb(Object s) {
        return (Map<String, Long>) ReflectionTestUtils.getField(s, "perDatabase");
    }

    @Test
    void tekVeritabani() {
        Object s = parse("# Keyspace\r\ndb0:keys=1234,expires=100,avg_ttl=350000\r\n");

        assertThat(totalKeys(s)).isEqualTo(1234L);
        assertThat(totalExpires(s)).isEqualTo(100L);
        assertThat(avgTtl(s)).isEqualTo(350_000L);
        assertThat(perDb(s)).containsExactly(Map.entry("db0", 1234L));
    }

    @Test
    void birdenFazlaVeritabaniToplanir() {
        Object s = parse(String.join("\r\n",
                "# Keyspace",
                "db0:keys=1000,expires=800,avg_ttl=300000",
                "db1:keys=500,expires=200,avg_ttl=100000",
                ""));

        assertThat(totalKeys(s)).isEqualTo(1500L);
        assertThat(totalExpires(s)).isEqualTo(1000L);
        assertThat(perDb(s)).containsOnlyKeys("db0", "db1");
        assertThat(perDb(s).get("db0")).isEqualTo(1000L);
        assertThat(perDb(s).get("db1")).isEqualTo(500L);
    }

    @Test
    void avgTtl_expiresSayisiylaAgirliklandirilir() {
        // (300000*800 + 100000*200) / 1000 = 260000
        Object s = parse(String.join("\r\n",
                "db0:keys=1000,expires=800,avg_ttl=300000",
                "db1:keys=500,expires=200,avg_ttl=100000",
                ""));

        assertThat(avgTtl(s)).isEqualTo(260_000L);
    }

    @Test
    void expireTanimliKeyYoksa_avgTtlNullDonerSifirDegil() {
        Object s = parse("db0:keys=10,expires=0,avg_ttl=0\r\n");

        assertThat(totalKeys(s)).isEqualTo(10L);
        assertThat(totalExpires(s)).isZero();
        assertThat(avgTtl(s)).isNull();
    }

    @Test
    void nullGovde_sifirlarVeNullTtl() {
        Object s = parse(null);

        assertThat(totalKeys(s)).isZero();
        assertThat(totalExpires(s)).isZero();
        assertThat(avgTtl(s)).isNull();
        assertThat(perDb(s)).isEmpty();
    }

    @Test
    void dbIleBaslamayanSatirlarAtlanir() {
        Object s = parse(String.join("\r\n",
                "# Keyspace",
                "",
                "some_other_field:123",
                "db0:keys=5,expires=1,avg_ttl=1000",
                ""));

        assertThat(totalKeys(s)).isEqualTo(5L);
        assertThat(perDb(s)).containsOnlyKeys("db0");
    }

    @Test
    void bozukSegmentSessizceAtlanir() {
        Object s = parse("db0:keys=abc,expires=3,avg_ttl=1000\r\n");

        assertThat(totalKeys(s)).isZero();       // "abc" ayrıştırılamadı
        assertThat(totalExpires(s)).isEqualTo(3L);
    }
}