package com.ucmd.monitor;

import com.ucmd.config.KeySecurityConfig;
import com.ucmd.enums.MetricStatus;
import com.ucmd.security.KeyMasker;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/** Rehber §9 — ACL reddi erişilemezlikten ayrılmalı. */
class RedisMonitorFailureStatusTest {

    private final RedisMonitor monitor =
            new RedisMonitor(null, new KeySecurityConfig(), new KeyMasker(true));

    private MetricStatus status(Exception e) {
        return ReflectionTestUtils.invokeMethod(monitor, "failureStatus", e);
    }

    @Test
    void noperm_permissionDenied() {
        assertThat(status(new RuntimeException(
                "NOPERM User ucmdmon has no permissions to run the 'scan' command")))
                .isEqualTo(MetricStatus.PERMISSION_DENIED);
    }

    @Test
    void wrongpass_permissionDenied() {
        assertThat(status(new RuntimeException("WRONGPASS invalid username-password pair")))
                .isEqualTo(MetricStatus.PERMISSION_DENIED);
    }

    @Test
    void noauth_permissionDenied() {
        assertThat(status(new RuntimeException("NOAUTH Authentication required.")))
                .isEqualTo(MetricStatus.PERMISSION_DENIED);
    }

    @Test
    void baglantiHatasi_unavailable() {
        assertThat(status(new IOException("Connection refused: localhost/127.0.0.1:6379")))
                .isEqualTo(MetricStatus.UNAVAILABLE);
    }

    @Test
    void zamanAsimi_unavailable() {
        assertThat(status(new TimeoutException("Command timed out after 5 second(s)")))
                .isEqualTo(MetricStatus.UNAVAILABLE);
    }

    @Test
    void mesajsizException_unavailable() {
        assertThat(status(new RuntimeException())).isEqualTo(MetricStatus.UNAVAILABLE);
    }
}