package com.nkolay.ucmd.monitor.prometheus;

import java.util.*;

/**
 * Tek bir node'un tüm metrikleri + sorgulama API'si.
 *
 * Bulunamayan metrik için {@code null} döner — sahte sıfır rehber §12'nin ihlalidir.
 * Çağıran taraf null kontrolüyle UNAVAILABLE statüsü set eder.
 */
public class NodeMetrics {

    /** Hazelcast metriklerinin ortak ön eki. */
    private static final String HZ_PREFIX = "com_hazelcast_metrics_";

    /** PrometheusTextParser'ın tag0'dan çıkardığı nesne adı anahtarı. */
    static final String OBJECT_LABEL = "__object";

    private final List<ParsedMetric> metrics;
    private final Map<String, List<ParsedMetric>> byName;

    public NodeMetrics(List<ParsedMetric> metrics) {
        this.metrics = metrics;
        this.byName = new HashMap<>();
        for (ParsedMetric m : metrics) {
            byName.computeIfAbsent(m.name(), k -> new ArrayList<>()).add(m);
        }
    }

    public boolean isEmpty() { return metrics.isEmpty(); }

    public List<ParsedMetric> all() { return metrics; }

    /** Etiketsiz veya tek örnekli metrik. Bulunamazsa null. */
    public Double value(String name) {
        List<ParsedMetric> candidates = byName.get(name);
        if (candidates == null || candidates.isEmpty()) return null;

        // Birden fazla örnek varsa etiketsiz olanı tercih et (belirsizliği önler)
        for (ParsedMetric m : candidates) {
            if (m.labels().isEmpty()) return m.value();
        }
        return candidates.get(0).value();
    }

    /** Belirli etiket eşleşmesiyle. Örn: value("jvm_memory_bytes_used", "area", "heap") */
    public Double value(String name, String labelKey, String labelValue) {
        List<ParsedMetric> candidates = byName.get(name);
        if (candidates == null) return null;
        for (ParsedMetric m : candidates) {
            if (m.hasLabel(labelKey, labelValue)) return m.value();
        }
        return null;
    }

    /** Hazelcast: prefix bazlı. Örn: hz("runningcount", "operation") */
    public Double hz(String metricSuffix, String prefix) {
        List<ParsedMetric> candidates = byName.get(HZ_PREFIX + metricSuffix);
        if (candidates == null) return null;

        ParsedMetric firstMatch = null;
        for (ParsedMetric m : candidates) {
            if (!m.hasLabel("prefix", prefix)) continue;
            // Nesne adı taşımayan örnek varsa onu tercih et
            if (m.label(OBJECT_LABEL) == null) return m.value();
            if (firstMatch == null) firstMatch = m;
        }
        return firstMatch != null ? firstMatch.value() : null;
    }

    /** Hazelcast: prefix + nesne adı. Örn: hz("ownedentrycount", "map", "ucmd-merchants") */
    public Double hz(String metricSuffix, String prefix, String objectName) {
        List<ParsedMetric> candidates = byName.get(HZ_PREFIX + metricSuffix);
        if (candidates == null) return null;
        for (ParsedMetric m : candidates) {
            if (m.hasLabel("prefix", prefix) && m.hasLabel(OBJECT_LABEL, objectName)) {
                return m.value();
            }
        }
        return null;
    }

    /** Belirli prefix altındaki tüm nesne adları. Örn: objectNames("map") */
    public Set<String> objectNames(String prefix) {
        Set<String> names = new LinkedHashSet<>();
        for (ParsedMetric m : metrics) {
            if (!m.hasLabel("prefix", prefix)) continue;
            String object = m.label(OBJECT_LABEL);
            if (object != null) names.add(object);
        }
        return names;
    }

    /**
     * Belirli etiketin tüm değerleri için toplam.
     * Örn: sumByLabel("jvm_gc_collection_seconds_count", "gc")
     * Hiç örnek yoksa null.
     */
    public Double sumByLabel(String name, String labelKey) {
        List<ParsedMetric> candidates = byName.get(name);
        if (candidates == null || candidates.isEmpty()) return null;

        double total = 0;
        boolean found = false;
        for (ParsedMetric m : candidates) {
            if (m.label(labelKey) == null) continue;
            total += m.value();
            found = true;
        }
        return found ? total : null;
    }
}
