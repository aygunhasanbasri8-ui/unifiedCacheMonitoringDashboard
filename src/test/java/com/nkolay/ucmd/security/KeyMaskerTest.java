package com.nkolay.ucmd.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.setPrintAssertionsDescription;

public class KeyMaskerTest {
    private final KeyMasker enabled = new KeyMasker(true);
    private final KeyMasker disabled = new KeyMasker(false);

    @ParameterizedTest
    @CsvSource({
            "ucmd:merchant:88123:commission,ucmd:merchant:***:commission",
            "ucmd:terminal:1234567:status,ucmd:terminal:***:status",
            "session:4821:user:7391,session:***:user:***",
            "ucmd:merchant:12:flag,ucmd:merchant:12:flag",
            "ucmd:tx:99:11:id,ucmd:tx:99:11:id"
    })
    void ucVeUzeriRakamDizileriMaskelenir(String input, String expected) {
        assertThat(enabled.mask(input)).isEqualTo(expected);
    }

    @Test
    void maskeKapalıyken_maskDegistirmez() {
        assertThat(disabled.mask("ucmd:merchant:88123:commission")).isEqualTo("ucmd:merchant:88123:commission");

    }

    @Test
    void maskForLog_yapilandirmadanBagimsizMaskeler() {
        assertThat(disabled.mask("ucmd:merchant:88123:commission")).isEqualTo("ucmd:merchant:***:commission");
    }

    @Test
    void nullGirdiKorunur() {
        assertThat(enabled.mask(null)).isNull();
        assertThat(disabled.mask(null)).isNull();
    }

    @Test
    void isEnabled_constructionArgumaniniYansitir() {
        assertThat(enabled.isEnabled()).isTrue();
        assertThat(disabled.isEnabled()).isFalse();
    }

}
