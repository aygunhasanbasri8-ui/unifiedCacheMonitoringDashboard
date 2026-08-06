package com.nkolay.ucmd.model.dto;

public record MetricEnvelope<T>(
        String name,
        T value,
        String unit,
        String technology,
        String scope,
        MetricStatus status,
        boolean estimated,
        MetricMeta meta
) {
    public static <T> MetricEnvelope<T> of (String name, T value, String unit, String technology, MetricStatus status ) {
        return new MetricEnvelope<>(name, value, unit, technology, "INSTANCE", status, status == MetricStatus.ESTIMATED, new MetricMeta());
    }
}
