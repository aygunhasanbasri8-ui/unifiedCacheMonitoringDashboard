# ADR-0008: Kısa TTL cache ve STALE fallback stratejisi

**Durum:** Kabul edildi
**Tarih:** 2026-08-06

## Bağlam

Dashboard 5 saniyede bir 8 kartı yeniliyor. Her kart bir metrik ucuna karşılık geliyor.
Tek kullanıcı dakikada ~96 istek üretiyor; on kullanıcıda bu sayı Redis'e giden ~1000
`INFO` çağrısına dönüşüyor.

Ayrıca okuma uçları POST kullandığı için (ADR-0007) HTTP cache devre dışı — cache
sunucu tarafında yapılmak zorunda.

İkinci problem: hedef sistem birkaç saniye erişilemez olduğunda dashboard'ın tüm
kartları anında boşalıyor, operatör "her şey çöktü" izlenimi alıyordu.

## Karar

İki katmanlı Caffeine cache:

| Cache | TTL | Amaç |
|---|---|---|
| `metricsCache` | 10 saniye | Taze veri; bu süre içinde hedef sisteme gidilmez |
| `lastKnownCache` | 30 dakika | Son başarılı değer; hata durumunda `STALE` olarak sunulur |

Anahtar: `{instanceId}:{operation}`. Maksimum 500 giriş.

**Akış:**
1. `metricsCache` hit → `FRESH` döner, hiçbir limit tüketilmez
2. Miss → koruma katmanından geçip hedef sisteme gidilir; başarılıysa iki cache'e de yazılır
3. Hata → `lastKnownCache`'e bakılır:
   - Değer varsa → `STALE` (30 dakikaya kadar)
   - Yoksa → `UNAVAILABLE`, zaman aşımıysa `TIMEOUT`

**STALE görünürlüğü:** `MetricMeta.stale = true` ve `ageMillis` doldurulur; DTO'nun
`overallStatus` alanı `STALE` olur. Frontend verinin bayat olduğunu gösterebilir.

**Sağlık ucunda özel kural:** Bayat sağlık bilgisi `UP` gösterilmez. `getInstanceHealth`
STALE durumunda `DOWN` döner — "10 dakika önce ayaktaydı" bilgisi operatörü yanıltır.

Instance yapılandırması değiştiğinde (`updateInstance`, `deleteInstance`) o
instance'ın tüm cache girdileri `executor.invalidate(instance)` ile temizlenir.

## Gerekçe

**Neden 10 saniye?** Dashboard 5 saniyede bir yeniliyor; 10 saniye TTL istek sayısını
yarıdan fazla düşürüyor ama veriyi "gerçek zamanlıya yakın" tutuyor. Daha uzun TTL
(30-60 sn) ani yük artışlarını gizlerdi.

**Neden STALE penceresi 30 dakika?** Kısa bir kesintide (deploy, yeniden başlatma)
dashboard'ın anlamlı kalması için yeterince uzun; verinin "tarihsel" sayılacağı kadar
uzun değil. 30 dakikayı aşan kesintide `UNAVAILABLE` doğru cevaptır.

**Neden ayrı iki cache, tek cache + uzun TTL değil?** Tek cache olsaydı 30 dakika
boyunca bayat veri `FRESH` olarak sunulurdu. İki katman "taze mi, bayat mı" ayrımını
mümkün kılıyor.

**Neden Caffeine, Redis cache değil?** UCMD'nin kendi cache'ini izlediği Redis'e
koymak dairesel bağımlılık yaratırdı: Redis çöktüğünde cache de çöker, STALE fallback
işe yaramaz. Süreç içi cache bu senaryoda ayakta kalıyor.

**Elenen alternatif — cache yok, doğrudan rate limit:** Dashboard kartları rastgele
429 alırdı; kullanıcı deneyimi kabul edilemez.

## Sonuçlar

**Artılar**
- Hedef sisteme giden istek sayısı ~%80 azaldı
- Kısa kesintilerde dashboard boşalmıyor, "eski veri" etiketiyle çalışmaya devam ediyor
- Cache en dışta olduğu için hit durumunda koruma katmanı kotası tüketilmiyor

**Eksiler / kabul edilen ödünler**
- Metrikler en fazla 10 saniye eski olabilir; gerçek zamanlı izleme değil (bilinçli)
- Bellek: 500 giriş × instance sayısı × DTO boyutu. Şu ölçekte önemsiz, çok sayıda
  instance'ta gözden geçirilmeli
- Süreç içi cache olduğu için UCMD yatay ölçeklenirse her örneğin kendi cache'i olur;
  aynı anda farklı örneklerden farklı tazelikte veri dönebilir
- `lastKnownCache` yapılandırma değişikliğinde temizlenmezse yanlış instance'ın
  verisi sunulabilir; bu yüzden `invalidate` çağrıları kritik
