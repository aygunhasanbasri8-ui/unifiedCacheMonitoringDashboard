# ADR-0003: `MetricStatus` durum modeli — neden her metrik statülü döner

**Durum:** Kabul edildi
**Tarih:** 2026-08-06

## Bağlam

Bir izleme aracında `0` değerinin en az dört farklı anlamı olabilir:

- gerçekten sıfır (hiç eviction olmamış)
- metrik alınamadı (instance erişilemiyor)
- bu teknolojide böyle bir metrik yok (Redis'te "partition sayısı")
- metrik henüz hesaplanmadı (CPU delta için ikinci örnek gerekiyor)

Projenin temel gereksinimi bu ayrımı zorunlu kılıyor: *"erişilemeyen metrikler yanlış değer üretmek
yerine UNAVAILABLE dönmelidir."* Dashboard'da "eviction: 0" gösterip aslında Redis'e
ulaşılamadığını gizlemek, operatörü yanlış yönlendirir — en kötü izleme hatası budur.

## Karar

Sekiz durumlu bir enum tanımlandı:

| Durum | Anlamı |
|---|---|
| `AVAILABLE` | Gerçek, taze veri |
| `UNAVAILABLE` | Erişilemedi veya hata verdi |
| `NOT_APPLICABLE` | Bu instance tipinde geçerli değil |
| `PERMISSION_DENIED` | ACL yetkisi yok (`NOPERM`/`WRONGPASS`/`NOAUTH`) |
| `TIMEOUT` | Çağrı zaman aşımına uğradı |
| `STALE` | Son bilinen değer, güncel değil |
| `ESTIMATED` | Örneklem/türetilmiş, kesin değil |
| `NOT_READY` | Metrik henüz hesaplanamadı |

Kurallar:
1. Hiçbir metrik DTO'su `null` dönmez — her zaman bir nesne, içinde statü
2. Her metrik DTO'sunda `MetricMeta meta` alanı bulunur (`collectedAt`, `ageMillis`, `stale`)
3. Alt-metrik grupları kendi statüsünü taşır: `ResourceStats` içinde `cpuStats`,
   `memoryStatus`, `gcStatus`, `threadStatus`, `fileDescriptorStatus` ayrı ayrı
4. Değeri olmayan alanlar wrapper tip (`Double`/`Long`/`Integer`) ve `null` olur;
   primitif `0` ile doldurulmaz

## Gerekçe

**Neden ayrı `PERMISSION_DENIED`?** ACL'e geçen bir kurulumda "erişilemiyor" ile
"yetkim yok" tamamen farklı müdahale gerektirir: biri ağ/servis sorunu, diğeri
yapılandırma. Redis `NOPERM` yanıtını mesajdan ayırt edip bu statüye eşliyoruz.

**Neden ayrı `ESTIMATED`?** Hazelcast'te hit oranı map sayaçlarından türetiliyor,
Redis'in `keyspace_hits` sayacı kadar kesin değil. Top-N key taraması sınırlı örneklem
üzerinden yapılıyor. Bu değerleri `AVAILABLE` göstermek yanıltıcı olurdu.

**Neden ayrı `NOT_READY`?** Redis CPU kullanımı iki `INFO cpu` örneği arasındaki
delta ile hesaplanır. İlk çağrıda hesaplanamaz — `0%` göstermek "CPU boşta" anlamına
gelir ki yanlıştır.

**Neden alt-metrik bazında statü?** Bir çağrıda `INFO memory` başarılı olup CPU delta
hazır olmayabilir. Tek bir `overallStatus` bu kısmi başarıyı ifade edemezdi.

**Elenen alternatif — HTTP durum kodları ile ifade:** Kısmi başarıyı taşıyamaz, ayrıca
"instance var ama erişilemiyor" durumunda 200 dönmek gerekiyor (bkz. ADR-0005).

## Sonuçlar

**Artılar**
- Dashboard "veri yok" ile "sıfır" ayrımını yapabiliyor; kartlarda gri/kırmızı ayrımı mümkün
- `MetricMeta.ageMillis` ile verinin ne kadar eski olduğu görülüyor
- Yeni durumlar (`TIMEOUT`, `PERMISSION_DENIED`) modele sonradan sorunsuz eklendi

**Eksiler / kabul edilen ödünler**
- DTO'lar şişkin: `ResourceStats` 14 metrik alanı + 8 statü alanı taşıyor
- Frontend her alan için statü kontrolü yapmak zorunda; kart mantığı daha karmaşık
- Her yeni metrik için "hangi statü grubuna girer" kararı gerekiyor
- Modelin dürüstlüğü uygulamaya bağlı: statüyü yanlış set eden bir kod yolu modeli
  sessizce bozar. Bilinen üç ihlal `docs/known-limitations.md` madde 11–13'te
