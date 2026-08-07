# ADR-0005: Hibrit instance politikası (404 vs 200 + UNAVAILABLE)

**Durum:** Kabul edildi
**Tarih:** 2026-08-06

## Bağlam

`POST /v1/instances/5/health` isteğine ne dönmeli?

İki farklı başarısızlık senaryosu var ve ilk tasarımda ikisi de aynı şekilde ele
alınıyordu:

1. **5 numaralı instance kaydı yok** — istemci hatalı istek yaptı
2. **Kayıt var ama Redis kapalı** — istemci doğru istek yaptı, izlenen sistem sorunlu

Hepsine 404 dönmek, dashboard'ın "instance silinmiş" sanmasına yol açıyordu. Hepsine
200 dönmek ise yazım hatasıyla var olmayan id'ye istek atıldığında sorunu gizliyordu.

## Karar

Hibrit politika:

| Durum | Yanıt |
|---|---|
| Instance DB'de yok | **404** · `instance-not-found` (`InstanceNotFoundException`) |
| Instance tipi desteklenmiyor | **400** · `bad-request` (`IllegalArgumentException`) |
| Instance var, cache'e erişilemiyor | **200** + `MetricStatus.UNAVAILABLE` |
| Instance var, koruma katmanı reddetti | **429 / 503 / 504** (bkz. ADR-0006) |

Uygulama kuralı: `getInstanceById(id)` `orElseThrow(...)` kullanır ve servis
metotlarında instance ile monitor çözümlemesi **`try` bloğunun dışında** yapılır.
Böylece kayıt yokluğu asla `UNAVAILABLE`'a dönüşmez.

```java
InstanceRef instance = getInstanceById(id);              // yoksa 404
CacheMonitor monitor = monitorRegistry.getMonitor(instance); // desteklenmiyorsa 400
var result = executor.executeCachedMetric(instance, "health",
        () -> monitor.getHealth(instance));              // erişilemezse UNAVAILABLE
```

## Gerekçe

**Neden erişilemezlik 404 değil?** 404 "böyle bir kaynak yok" demektir. Instance kaydı
duruyor, yalnızca hedef sistem cevap vermiyor. Dashboard bu instance'ı listeden
çıkarmamalı, "erişilemiyor" olarak göstermeli.

**Neden erişilemezlik 503 değil?** 503 UCMD'nin kendisinin hizmet veremediğini ima
eder. UCMD çalışıyor ve doğru cevabı veriyor: "izlediğim sistem şu an erişilemez."
Bu bir veri durumu, sunucu hatası değil. Ayrıca dashboard tek sayfada 8 kart
gösteriyor; birinin 503 dönmesi istemci tarafında genel hata işleyicisini tetikleyip
tüm sayfayı bozardı.

Not: Koruma katmanı reddi (bulkhead dolu, circuit açık) **gerçekten** 503 döner —
orada UCMD bilinçli olarak isteği kabul etmiyor.

**Neden tip hatası 400?** Desteklenmeyen tip bir yapılandırma/girdi hatasıdır,
sessizce `UNAVAILABLE` dönmek sorunu gizlerdi.

**Elenen alternatif — her şeye 200 + statü:** Var olmayan id'ye yapılan isteği
gizlerdi; entegrasyon hatalarının fark edilmesi zorlaşırdı.

## Sonuçlar

**Artılar**
- Dashboard erişilemeyen instance'ı listede tutup gri gösterebiliyor
- Yazım hatası / silinmiş kayıt anında fark ediliyor
- Snapshot toplayıcı DOWN durumunu kaydedip zaman serisini kesintisiz tutabiliyor

**Eksiler / kabul edilen ödünler**
- Servis metotlarında disiplin gerektiriyor: `getInstanceById` yanlışlıkla `try`
  içine alınırsa 404 sessizce 200'e döner. Kod incelemesinde bakılan noktalardan biri
- İstemci "200 aldım" diye veriyi doğru sanabilir; statü alanını kontrol etmek
  zorunlu. Bu yüzden hiçbir DTO `null` dönmüyor (ADR-0003)
- `/snapshots` ve `/history` uçlarında bu kontrol başlangıçta unutulmuştu; olmayan id
  için 200 + boş liste dönüyordu. Sonradan düzeltildi
