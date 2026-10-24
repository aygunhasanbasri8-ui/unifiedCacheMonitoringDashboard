package com.ucmd.enums;

/**
 * Alarm kuralının baktığı değer. Hepsi zamanlayıcının zaten topladığı
 * snapshot'tan okunur; alarm için izlenen sisteme ek istek atılmaz (ADR-0001).
 */
public enum AlertMetric {
    /** Instance'a ulaşılamıyor; eşik kullanılmaz. */
    INSTANCE_DOWN,
    /** memory_used / memory_max, yüzde. maxmemory tanımsızsa hesaplanmaz. */
    MEMORY_PERCENT,
    CPU_PERCENT,
    /** Yüzde; hesaplanamadığında (null) kural o turu atlar. */
    HIT_RATIO_PERCENT,
    FRAGMENTATION_RATIO,
    CLIENT_COUNT,
    /** Master'a bağlı replika sayısı; replika düşünce "LT 1" gibi kullanılır. */
    CONNECTED_REPLICAS,
    /** Son iki snapshot arasında dakikada atılan key sayısı. */
    EVICTIONS_PER_MINUTE
}
