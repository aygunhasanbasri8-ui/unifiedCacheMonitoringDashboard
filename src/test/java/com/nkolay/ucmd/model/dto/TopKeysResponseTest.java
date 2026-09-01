package com.nkolay.ucmd.model.dto;

import com.nkolay.ucmd.enums.MetricStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Örnekleme (sampling) — tarama tamamlanmadıysa sonuç ESTIMATED olmalı. */
class TopKeysResponseTest {

    private static KeyInfo key(String name) {
        return new KeyInfo(name, "string", 128, -1);
    }

    @Test
    void taramaTamamlandiysa_kesinSonuc() {
        TopKeysResponse r = new TopKeysResponse(List.of(key("a")), 1200, 2000, true);

        assertThat(r.isScanCompleted()).isTrue();
        assertThat(r.isEstimated()).isFalse();
        assertThat(r.getStatus()).isEqualTo(MetricStatus.AVAILABLE);
        assertThat(r.getScannedCount()).isEqualTo(1200);
        assertThat(r.getScanLimit()).isEqualTo(2000);
    }

    @Test
    void limitteKesildiyse_estimated() {
        TopKeysResponse r = new TopKeysResponse(List.of(key("a")), 2000, 2000, false);

        assertThat(r.isScanCompleted()).isFalse();
        assertThat(r.isEstimated()).isTrue();
        assertThat(r.getStatus()).isEqualTo(MetricStatus.ESTIMATED);
    }

    @Test
    void nullKeyListesi_bosListeyeDonusur() {
        TopKeysResponse r = new TopKeysResponse(null, 0, 2000, true);

        assertThat(r.getKeys()).isNotNull().isEmpty();
    }

    @Test
    void unavailable_keySizVeUnavailableStatu() {
        TopKeysResponse r = TopKeysResponse.unavailable();

        assertThat(r.getStatus()).isEqualTo(MetricStatus.UNAVAILABLE);
        assertThat(r.getKeys()).isEmpty();
        assertThat(r.getMeta()).isNotNull();
    }
}