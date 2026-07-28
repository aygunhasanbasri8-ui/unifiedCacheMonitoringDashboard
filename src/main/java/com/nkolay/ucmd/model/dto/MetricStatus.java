package com.nkolay.ucmd.model.dto;

public enum MetricStatus {
    ///GERÇEK VERİ
    AVAILABLE,
    ///ULAŞILAMADI/HATA VERDİ
    UNAVAILABLE,
    ///INSTANCE TÜRÜNE GÖRE GEÇERLİ DEĞİL
    NOT_APPLICABLE,
    ///SNAPSHOT DEĞERİ/ESKİ OLABİLİR
    STALE,
    ///YAKLAŞIK DEĞER
    ESTIMATED,
    ///METRİK HENÜZ HESAPLANMADI
    NOT_READY
}
