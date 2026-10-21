package com.ucmd.monitor;

import com.ucmd.config.KeySecurityConfig;
import com.ucmd.security.KeyMasker;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/** Redis INFO ayrıştırıcıları. Metotlar private — ReflectionTestUtils ile çağrılıyor. */
class RedisMonitorInfoParserTest {

    private final RedisMonitor monitor =
            new RedisMonitor(null, new KeySecurityConfig(), new KeyMasker(true));

    private static final String INFO_MEMORY = String.join("\r\n",
            "# Memory",
            "used_memory:1048576",
            "used_memory_rss:2097152",
            "used_memory_human:1.00M",
            "maxmemory:0",
            "mem_fragmentation_ratio:1.25",
            "mem_fragmentation_bytes:524288",
            "");

    private String infoValue(String info, String field, String def) {
        return ReflectionTestUtils.invokeMethod(monitor, "infoValue", info, field, def);
    }

    private long infoLong(String info, String field, long def) {
        Long v = ReflectionTestUtils.invokeMethod(monitor, "infoLong", info, field, def);
        return v == null ? def : v;
    }

    private double infoDouble(String info, String field, double def) {
        Double v = ReflectionTestUtils.invokeMethod(monitor, "infoDouble", info, field, def);
        return v == null ? def : v;
    }

    @Test
    void infoValue_alaniBulur() {
        assertThat(infoValue(INFO_MEMORY, "used_memory_human", "?")).isEqualTo("1.00M");
    }

    @Test
    void infoValue_alanYoksaVarsayilanDoner() {
        assertThat(infoValue(INFO_MEMORY, "olmayan_alan", "varsayilan")).isEqualTo("varsayilan");
    }

    @Test
    void infoValue_nullGovdedeVarsayilanDoner() {
        assertThat(infoValue(null, "used_memory", "yok")).isEqualTo("yok");
    }

    @Test
    void infoValue_onEkiAyniAlanlariKaristirmaz() {
        // "used_memory" araması "used_memory_rss" satırına düşmemeli
        assertThat(infoLong(INFO_MEMORY, "used_memory", -1)).isEqualTo(1_048_576L);
        assertThat(infoLong(INFO_MEMORY, "used_memory_rss", -1)).isEqualTo(2_097_152L);
    }

    @Test
    void infoValue_lfSatirSonuIleDeCalisir() {
        String lfOnly = "# Server\nredis_version:7.2.4\nuptime_in_seconds:86400\n";

        assertThat(infoValue(lfOnly, "redis_version", "unknown")).isEqualTo("7.2.4");
        assertThat(infoLong(lfOnly, "uptime_in_seconds", 0)).isEqualTo(86_400L);
    }

    @Test
    void infoLong_sifirDegerleriVarsayilanlaKaristirmaz() {
        assertThat(infoLong(INFO_MEMORY, "maxmemory", -1)).isZero();
    }

    @Test
    void infoLong_ondalikDegerdeVarsayilanaDuser() {
        // "1.25" long olarak ayrıştırılamaz → varsayılan
        assertThat(infoLong(INFO_MEMORY, "mem_fragmentation_ratio", -7)).isEqualTo(-7L);
    }

    @Test
    void infoLong_alanYoksaVarsayilanDoner() {
        assertThat(infoLong(INFO_MEMORY, "olmayan_alan", 42)).isEqualTo(42L);
    }

    @Test
    void infoDouble_ondalikAyristirir() {
        assertThat(infoDouble(INFO_MEMORY, "mem_fragmentation_ratio", 1.0)).isEqualTo(1.25);
    }

    @Test
    void infoDouble_alanYoksaVarsayilanDoner() {
        assertThat(infoDouble(INFO_MEMORY, "olmayan_alan", 9.5)).isEqualTo(9.5);
    }

    @Test
    void infoDouble_tamsayiDegeriDeOkur() {
        assertThat(infoDouble(INFO_MEMORY, "used_memory", 0)).isEqualTo(1_048_576.0);
    }
}