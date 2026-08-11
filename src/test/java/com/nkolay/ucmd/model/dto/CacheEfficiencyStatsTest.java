package com.nkolay.ucmd.model.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Hit ratio hesabı — yoksa 0 değil NOT_APPLICABLE. */
class CacheEfficiencyStatsTest {

    @Test
    void hitRatio_hitVeMissToplamindanHesaplanir() {
        CacheEfficiencyStats s = new CacheEfficiencyStats(750, 250);

        assertThat(s.getHitRatio()).isEqualTo(0.75);
        assertThat(s.getMissRatio()).isEqualTo(0.25);
        assertThat(s.getOverallStatus()).isEqualTo(MetricStatus.AVAILABLE);
    }

    @Test
    void oranlarinToplamiBirdir() {
        CacheEfficiencyStats s = new CacheEfficiencyStats(7, 3);

        assertThat(s.getHitRatio() + s.getMissRatio()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void trafikYoksa_notApplicableVeNullOran() {
        CacheEfficiencyStats s = new CacheEfficiencyStats(0, 0);

        assertThat(s.getHitRatio()).isNull();
        assertThat(s.getMissRatio()).isNull();
        assertThat(s.getOverallStatus()).isEqualTo(MetricStatus.NOT_APPLICABLE);
    }

    @Test
    void tamamiHit_oranBir() {
        CacheEfficiencyStats s = new CacheEfficiencyStats(100, 0);

        assertThat(s.getHitRatio()).isEqualTo(1.0);
        assertThat(s.getMissRatio()).isEqualTo(0.0);
        assertThat(s.getOverallStatus()).isEqualTo(MetricStatus.AVAILABLE);
    }

    @Test
    void tamamiMiss_oranSifir() {
        CacheEfficiencyStats s = new CacheEfficiencyStats(0, 100);

        assertThat(s.getHitRatio()).isEqualTo(0.0);
        assertThat(s.getMissRatio()).isEqualTo(1.0);
    }

    @Test
    void meta_hicbirZamanNullDegil() {
        assertThat(new CacheEfficiencyStats(1, 1).getMeta()).isNotNull();
    }
}