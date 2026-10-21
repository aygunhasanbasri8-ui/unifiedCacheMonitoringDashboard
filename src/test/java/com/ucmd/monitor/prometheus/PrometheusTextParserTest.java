package com.ucmd.monitor.prometheus;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** jmx_exporter scrape ayrıştırma — etiket, escape, tag0, bozuk satır. */
class PrometheusTextParserTest {

    private static final String SCRAPE = """
            # HELP process_open_fds Number of open file descriptors.
            # TYPE process_open_fds gauge
            process_open_fds 77.0

            jvm_memory_bytes_used{area="heap",} 1.30423192E8
            jvm_memory_bytes_used{area="nonheap",} 8.5E7
            jvm_gc_collection_seconds_count{gc="G1 Young Generation",} 12.0
            jvm_gc_collection_seconds_count{gc="G1 Old Generation",} 3.0
            process_start_time_seconds 1.7543E9 1754400000000
            com_hazelcast_metrics_ownedentrycount{instance="127.0.0.1:5701",prefix="map",tag0="\\"name=ucmd-merchants\\"",} 24.0
            com_hazelcast_metrics_ownedentrycount{instance="127.0.0.1:5701",prefix="map",tag0="\\"name=ucmd-terminals\\"",} 7.0
            com_hazelcast_metrics_runningcount{instance="127.0.0.1:5701",prefix="operation",} 3.0
            com_hazelcast_metrics_uptime{instance="127.0.0.1:5701",prefix="runtime",} 864000.0
            label_with_comma{note="a,b",} 1.0
            nan_metric NaN
            inf_metric +Inf
            unterminated_metric{foo="bar" 9.0
            """;

    private final NodeMetrics metrics = PrometheusTextParser.parse(SCRAPE);

    @Test
    void etiketsizSatirAyristirilir() {
        assertThat(metrics.value("process_open_fds")).isEqualTo(77.0);
    }

    @Test
    void etiketliSatirEtiketleSorgulanir() {
        assertThat(metrics.value("jvm_memory_bytes_used", "area", "heap"))
                .isEqualTo(1.30423192E8);
        assertThat(metrics.value("jvm_memory_bytes_used", "area", "nonheap"))
                .isEqualTo(8.5E7);
    }

    @Test
    void yorumVeBosSatirlarAtlanir() {
        assertThat(metrics.value("# HELP")).isNull();
        assertThat(metrics.value("#")).isNull();
    }

    @Test
    void satirSonundakiTimestampDegereKarismaz() {
        assertThat(metrics.value("process_start_time_seconds")).isEqualTo(1.7543E9);
    }

    @Test
    void tag0DanNesneAdiCikarilir() {
        assertThat(metrics.hz("ownedentrycount", "map", "ucmd-merchants")).isEqualTo(24.0);
        assertThat(metrics.hz("ownedentrycount", "map", "ucmd-terminals")).isEqualTo(7.0);
    }

    @Test
    void objectLabelEtiketOlarakYazilir() {
        ParsedMetric m = metrics.all().stream()
                .filter(p -> p.name().equals("com_hazelcast_metrics_ownedentrycount"))
                .filter(p -> "ucmd-merchants".equals(p.label(NodeMetrics.OBJECT_LABEL)))
                .findFirst()
                .orElseThrow();

        assertThat(m.hasLabel("prefix", "map")).isTrue();
        assertThat(m.value()).isEqualTo(24.0);
    }

    @Test
    void objectNames_prefixAltindakiTumAdlariDoner() {
        assertThat(metrics.objectNames("map"))
                .containsExactlyInAnyOrder("ucmd-merchants", "ucmd-terminals");
        assertThat(metrics.objectNames("operation")).isEmpty();
    }

    @Test
    void nesneAdiTasimayanPrefixSorgusu() {
        assertThat(metrics.hz("runningcount", "operation")).isEqualTo(3.0);
        assertThat(metrics.hz("uptime", "runtime")).isEqualTo(864000.0);
    }

    @Test
    void tirnakIcindekiVirgulAyiracDegildir() {
        ParsedMetric m = metrics.all().stream()
                .filter(p -> p.name().equals("label_with_comma"))
                .findFirst()
                .orElseThrow();

        assertThat(m.label("note")).isEqualTo("a,b");
    }

    @Test
    void nanVeSonsuzDegerlerAtlanir() {
        assertThat(metrics.value("nan_metric")).isNull();
        assertThat(metrics.value("inf_metric")).isNull();
    }

    @Test
    void kapanmayanSusluParantezliSatirAtlanir() {
        assertThat(metrics.value("unterminated_metric")).isNull();
    }

    @Test
    void bulunamayanMetrikNullDonerSifirDegil() {
        assertThat(metrics.value("olmayan_metrik")).isNull();
        assertThat(metrics.hz("olmayan", "map")).isNull();
        assertThat(metrics.hz("ownedentrycount", "map", "olmayan-map")).isNull();
    }

    @Test
    void sumByLabel_etiketliTumOrnekleriToplar() {
        assertThat(metrics.sumByLabel("jvm_gc_collection_seconds_count", "gc"))
                .isEqualTo(15.0);
        assertThat(metrics.sumByLabel("process_open_fds", "gc")).isNull();
    }

    @Test
    void bosVeyaNullGovde_bosNodeMetricsUretir() {
        assertThat(PrometheusTextParser.parse(null).isEmpty()).isTrue();
        assertThat(PrometheusTextParser.parse("").isEmpty()).isTrue();
    }
}