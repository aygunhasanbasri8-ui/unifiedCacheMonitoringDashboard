# ADR-0006: Koruma katmanı — resilience4j ile rate limit / bulkhead / circuit breaker

**Durum:** Kabul edildi
**Tarih:** 2026-08-06

## Bağlam

Projenin temel kuralı: **izleme aracı izlediği sistemi yormamalıdır.**

Somut riskler:
- Dashboard 5 saniyede bir 8 kart yeniliyor. 10 kullanıcı açık tutarsa saniyede
  ~16 istek doğrudan Redis'e gider
- Key taraması (`SCAN`, top-N, pattern istatistikleri) binlerce key dolaşır; iki
  kullanıcı aynı anda başlatırsa Redis'in tek thread'i tıkanır
- Hedef sistem yavaşladığında istekler birikir, UCMD'nin thread havuzu tükenir ve
  **UCMD de çöker** — izleme aracının arızayı büyütmesi kabul edilemez

## Karar

Tüm monitor çağrıları `ProtectedMonitorExecutor` üzerinden geçer. İstisna yoktur:
yeni bir monitor çağrısı eklendiğinde `executeCachedMetric` veya `executeExpensive`
ile sarılır.

Zincir sırası: **cache → rate limiter → bulkhead → circuit breaker → timeout → çağrı**

Cache en dışta olduğu için hit durumunda hiçbir limit tüketilmez.

**İki yürütme modu:**

| | `executeCachedMetric` | `executeExpensive` |
|---|---|---|
| Kullanım | Metrik uçları | SCAN tabanlı uçlar |
| Cache | 10 sn TTL + 30 dk STALE penceresi | Yok |
| Rate limiter | Uygulanmaz | Dakikada 10 / instance |
| Bulkhead | 5 eş zamanlı | 2 eş zamanlı |
| Timeout | 5 sn | 15 sn |
| Hata davranışı | Son bilinen değer `STALE`, yoksa `UNAVAILABLE` / `TIMEOUT` | Exception → 429 / 503 / 504 |

Limitler **instance bazlıdır**: resilience4j registry'den `"monitor-" + instanceId`
gibi dinamik isimlerle alınır. Bir Redis'in yavaşlaması diğerlerini etkilemez.

Tüm reddetmeler Micrometer sayaçlarına yazılır (`ucmd_protection_*`).

## Gerekçe

**Neden ucuz çağrılarda rate limit ve bulkhead reddi yok?** Karar 2.8: dashboard'ın
kilitlenmemesi. Metrik uçları zaten 10 saniyelik cache arkasında; gerçek yük düşük.
Rate limit uygulansaydı çok kullanıcılı ortamda kartlar rastgele "429" gösterirdi.

**Neden pahalı çağrılarda cache yok?** Karar 3.3: parametre uzayı çok geniş
(prefix × cursor × count × matchMode × mapName). Cache isabet oranı düşük, bellek
maliyeti yüksek olurdu.

**Neden cache zincirin en dışında?** Cache hit'te rate limiter token tüketmek,
dashboard'ın kendi cache'inden okurken kotasını yakmasına yol açardı.

**Neden circuit breaker?** Hedef sistem tamamen çöktüğünde her istek timeout süresince
bekler. Devre açıldığında istekler anında reddedilir — hem UCMD'nin thread'leri
serbest kalır hem de çöken sisteme yeniden bağlanma denemeleri azalır.

**Neden virtual thread + `Future.get(timeout)`?** Lettuce senkron API'si blokluyor.
Virtual thread ucuz olduğu için her çağrıya bir tane ayırmak sorun değil; timeout'u
dışarıdan uygulayabiliyoruz.

**Elenen alternatif — resilience4j anotasyonları (`@RateLimiter`, `@Bulkhead`):**
Anotasyonlar statik isim ister; instance bazlı dinamik limit kurulamıyordu.
Programatik registry kullanımı seçildi.

## Sonuçlar

**Artılar**
- İzlenen sistem korunuyor; tek instance'ın arızası diğerlerine yayılmıyor
- STALE fallback sayesinde geçici kesintide dashboard boşalmıyor, "eski veri" diyor
- Reddetme sayaçları Prometheus'ta; limitlerin doğru ayarlandığı ölçülebiliyor

**Eksiler / kabul edilen ödünler**
- Ek gecikme ve karmaşıklık: her çağrı 5 katmandan geçiyor
- Limit değerleri deneyseldir; üretim yüküne göre ayarlanması gerekecek
- `executeCachedMetric` tüm exception'ları yutup `UNAVAILABLE`'a çevirdiği için,
  içeride fırlatılan **iş kuralı** exception'ları da yutulur. Map allowlist kontrolü
  bu yüzden koruma katmanının dışına alınmak zorunda kaldı; benzer kontroller
  eklenirken bu tuzağa dikkat edilmeli
- Cache TTL'i 10 sn olduğundan metrikler en fazla 10 saniye eski olabilir; gerçek
  zamanlı izleme beklentisi karşılanmaz (bilinçli)
