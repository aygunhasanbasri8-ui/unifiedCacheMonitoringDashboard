# ADR-0004: RFC 7807 hata sözleşmesi

**Durum:** Kabul edildi
**Tarih:** 2026-08-06

## Bağlam

Başlangıçta hata yanıtları tutarsızdı: bazı uçlar `{"error": "..."}`, bazıları
Spring'in varsayılan `{"timestamp","status","error","path"}` yapısını, bazıları düz
metin dönüyordu. Frontend her uç için ayrı hata ayrıştırma yazmak zorundaydı.

Ayrıca bir üretim olayında "hangi istek hata verdi" sorusunu cevaplamak için istekle
log arasında bağ kurulamıyordu.

## Karar

Tüm hatalar `GlobalExceptionHandler` üzerinden `application/problem+json` içerik
tipiyle ve RFC 7807 yapısında döner:

```json
{
  "type": "https://ucmd.example.local/problems/instance-not-found",
  "title": "Instance Not Found",
  "status": 404,
  "detail": "Instance bulunamadı: 999",
  "instance": "/api/v1/instances/999/health",
  "traceId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": 1786026129547
}
```

Kurallar:
- `type` alanı `https://ucmd.example.local/problems/<suffix>` biçiminde; suffix makine
  tarafından okunabilir ayırt edici (`invalid-prefix`, `map-not-allowed`,
  `rate-limit-exceeded`, `circuit-open`, `monitor-timeout`, `instance-busy`, ...)
- `traceId` istekteki `X-Request-Id` header'ından alınır, yoksa üretilir
- Yeni exception eklendiğinde handler yazılır ve `build(status, typeSuffix, title,
  detail, request)` yardımcısı kullanılır
- `@ExceptionHandler(Exception.class)` **her zaman en sonda** kalır

Spring framework hataları da sözleşmeye çekildi: bozuk JSON, tip uyuşmazlığı, eksik
parametre, desteklenmeyen metot/medya tipi, bulunamayan uç.

## Gerekçe

**Neden RFC 7807?** Standart; frontend ve API gateway'ler tanıyor. Kendi şemamızı
uydurmak yerine yaygın olanı seçtik.

**Neden `type` alanı URI?** RFC gereği. Makine okunabilir ayırt edici sağlıyor —
frontend `status` koduna değil `type` suffix'ine göre dallanabiliyor. Örneğin hem
bulkhead dolması hem circuit açılması 503 döner ama `type` farklıdır.

**Neden `traceId` gövdede, sadece header'da değil?** Kullanıcı hata ekranının
ekran görüntüsünü paylaştığında traceId görünür olsun diye. Destek akışını hızlandırıyor.

**Neden `detail` alanında iç hata mesajı yok?** `Exception.class` handler'ı sabit
"An unexpected error occurred" döner; gerçek mesaj yalnızca loga yazılır. Yığın izi
veya SQL hata metni istemciye sızmamalı.

## Sonuçlar

**Artılar**
- Frontend tek hata ayrıştırıcı yazıyor
- `type` suffix'leri sözleşmenin parçası, kod dışından test edilebilir
- Beklenmeyen hatalarda iç detay sızmıyor

**Eksiler / kabul edilen ödünler**
- Her yeni exception için handler yazma disiplini gerekiyor; unutulursa catch-all
  devreye girip 500 döner
- `problem+json` içerik tipini tanımayan eski istemciler yanıtı indirilebilir dosya
  sanabilir
- Handler sırası önemli: `Exception.class` yukarı taşınırsa tüm özel handler'lar
  devre dışı kalır. Bu kırılganlık kod incelemesiyle korunuyor
