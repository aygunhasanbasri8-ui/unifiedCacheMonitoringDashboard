package com.nkolay.ucmd.model.dto;

public record MetricValue<T>(T value, MetricStatus status) {
    public static <T> MetricValue<T> available(T value){
        return new MetricValue<>(value, MetricStatus.AVAILABLE);
    }

    public static <T> MetricValue<T> unavailable() {
        return new MetricValue<>(null, MetricStatus.UNAVAILABLE);
    }

    public static <T> MetricValue<T> notApplicable() {
        return new MetricValue<>(null, MetricStatus.NOT_APPLICABLE);
    }

    public static <T> MetricValue<T> estimated(T value) {
        return new MetricValue<>(value, MetricStatus.ESTIMATED);
    }

    public static <T> MetricValue<T> notReady(T fallback) {
        return new MetricValue<>(fallback, MetricStatus.NOT_READY);
    }

    public static <T> MetricValue<T> stale(T value) {
        return new MetricValue<>(value, MetricStatus.STALE);
    }
}
