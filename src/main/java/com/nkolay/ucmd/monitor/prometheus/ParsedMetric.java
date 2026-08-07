package com.nkolay.ucmd.monitor.prometheus;

import java.util.Map;

/** Prometheus text format'tan ayrıştırılmış tek metrik satırı. */
public record ParsedMetric(String name, Map<String, String> labels, double value) {

    public String label(String key) {
        return labels.get(key);
    }

    public boolean hasLabel(String key, String expected) {
        return expected.equals(labels.get(key));
    }
}
