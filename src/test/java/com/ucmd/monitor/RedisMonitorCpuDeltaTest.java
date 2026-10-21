package com.ucmd.monitor;

import com.ucmd.config.KeySecurityConfig;
import com.ucmd.security.KeyMasker;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CPU delta hesabı instance bazlı. İlk örneklem her zaman NOT_READY;
 * ikinci çağrıda iki örneklem arası fark yüzdeye çevrilir ve 0-100'e kırpılır.
 */
class RedisMonitorCpuDeltaTest {

    private final RedisMonitor monitor =
            new RedisMonitor(null, new KeySecurityConfig(), new KeyMasker(true));

    private static String cpuInfo(double sys, double user) {
        return String.join("\r\n",
                "# CPU",
                "used_cpu_sys:" + sys,
                "used_cpu_user:" + user,
                "");
    }

    private double cpu(Long instanceId, String cpuInfo) {
        Double v = ReflectionTestUtils.invokeMethod(
                monitor, "calculateCpuPercent", instanceId, cpuInfo);
        return v == null ? Double.NaN : v;
    }

    @Test
    void ilkOrneklemNotReady() {
        assertThat(cpu(1L, cpuInfo(1.0, 2.0))).isEqualTo(RedisMonitor.CPU_NOT_READY);
    }

    @Test
    void asiriDeltaYuzeKirpilir() throws Exception {
        cpu(2L, cpuInfo(0.0, 0.0));
        Thread.sleep(20);

        assertThat(cpu(2L, cpuInfo(500.0, 500.0))).isEqualTo(100.0);
    }

    @Test
    void sayacSifirlanmasiSifiraKirpilir() throws Exception {
        cpu(3L, cpuInfo(100.0, 100.0));
        Thread.sleep(20);

        assertThat(cpu(3L, cpuInfo(1.0, 1.0))).isEqualTo(0.0);
    }

    @Test
    void gercekciDeltaGecerliAraliktaKalir() throws Exception {
        cpu(4L, cpuInfo(10.0, 20.0));
        Thread.sleep(100);

        assertThat(cpu(4L, cpuInfo(10.01, 20.01))).isBetween(0.0, 100.0);
    }

    @Test
    void orneklemlerInstanceBazindaIzole() {
        cpu(5L, cpuInfo(1.0, 1.0));

        // Farklı instance kendi ilk örneklemini alır, 5L'in örneklemini kullanmaz
        assertThat(cpu(6L, cpuInfo(1.0, 1.0))).isEqualTo(RedisMonitor.CPU_NOT_READY);
    }

    @Test
    void nullGirdilerNotReady() {
        assertThat(cpu(7L, null)).isEqualTo(RedisMonitor.CPU_NOT_READY);
        assertThat(cpu(null, cpuInfo(1.0, 1.0))).isEqualTo(RedisMonitor.CPU_NOT_READY);
    }
}