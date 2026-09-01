package com.nkolay.ucmd.model.dto;

import com.nkolay.ucmd.enums.MetricStatus;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;


public class PageInfoTest {

    @Test
    void totalElements_icerikBoyutundanTuretilir() {
        PageInfo<String> page = new PageInfo<>(List.of("a", "b", "c"), "17", true, 10);

        assertThat(page.getContent()).containsExactly("a", "b", "c");
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getCursor()).isEqualTo("17");
        assertThat(page.isHasMore()).isTrue();
        assertThat(page.getPageSize()).isEqualTo(10);
        assertThat(page.getStatus()).isEqualTo(MetricStatus.AVAILABLE);
    }

    @Test
    void nullIcerik_bosListeyeDonusurNullOlmaz() {
        PageInfo<String> page = new PageInfo<>(null, "0", false, 10);

        assertThat(page.getContent()).isNotNull().isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void sonSayfa_sifirCursorVeHasMoreFalse() {
        PageInfo<String> page = new PageInfo<>(List.of("a"), "0", false, 10);

        assertThat(page.getCursor()).isEqualTo("0");
        assertThat(page.isHasMore()).isFalse();
    }

    @Test
    void unavailable_bosIcerikVeUnavailableStatu() {
        PageInfo<KeyInfo> page = PageInfo.unavailable(25);

        assertThat(page.getStatus()).isEqualTo(MetricStatus.UNAVAILABLE);
        assertThat(page.getContent()).isEmpty();
        assertThat(page.getCursor()).isEqualTo("0");
        assertThat(page.isHasMore()).isFalse();
        assertThat(page.getPageSize()).isEqualTo(25);

    }

    @Test
    void meta_hicbirZamanNullDegil () {

        assertThat(new PageInfo<String>().getMeta()).isNotNull();
        assertThat(PageInfo.unavailable(10).getMeta()).isNotNull();
    }


}
