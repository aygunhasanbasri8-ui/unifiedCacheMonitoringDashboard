# ADR-0002: `CacheMonitor` strategy pattern ile teknoloji soyutlaması

**Durum:** Kabul edildi
**Tarih:** 2026-08-06

## Bağlam

UCMD iki farklı cache teknolojisini izler: Redis (Lettuce, RESP protokolü, `INFO`
tabanlı metrikler) ve Hazelcast (Java client, dağıtık nesneler, Prometheus scrape
tabanlı metrikler). İkisinin veri modeli ve metrik kaynağı temelde farklıdır —
Redis'te "key" tek bir değer, Hazelcast'te "IMap" içinde entry'ler vardır.

Servis ve controller katmanının her metrik çağrısında `if (type.equals("REDIS"))`
dallanması yapması, yeni teknoloji eklendiğinde onlarca yerin değişmesi demekti.

## Karar

Teknolojiye özgü tüm okuma mantığı `CacheMonitor` arayüzünün arkasına alındı.
Uygulamalar: `RedisMonitor`, `HazelcastMonitor`.

`MonitorRegistry`, Spring'in enjekte ettiği tüm `CacheMonitor` bean'lerini
`getSupportedType()` değerine göre bir haritada toplar ve `InstanceRef.type`'a göre
doğru uygulamayı seçer. Desteklenmeyen tip için `IllegalArgumentException` → **400**.

Bir teknolojide anlamı olmayan metrik `NOT_APPLICABLE` statüsüyle döner; arayüzden
metot çıkarılmaz. Örnek: `RedisMonitor.getMapStats()` → `MapStatsInfo.notApplicable()`,
`HazelcastMonitor.getReplicationInfo()` → `ReplicationInfo.notApplicable()`.

## Gerekçe

**Neden ortak arayüz, teknolojiye özel ayrı servisler değil?** Frontend tek bir
dashboard gösteriyor; her kart her instance tipi için aynı DTO'yu bekliyor. Ayrı
servisler olsaydı DTO dönüşümü controller'a taşınırdı.

**Neden `NOT_APPLICABLE` yerine metot dışlama (örn. `RedisMonitor implements
CacheMonitor, MapCapable`)?** Yetenek arayüzleri (capability interfaces) çağıran
tarafta yine `instanceof` kontrolü gerektirirdi. `NOT_APPLICABLE` statüsü hem tip
güvenli hem de frontend'e "bu metrik bu teknolojide yok" bilgisini açıkça taşıyor —
"veri gelmedi" ile "böyle bir şey yok" ayrımı korunuyor (bkz. ADR-0003).

**Elenen alternatif — reflection/annotation tabanlı dispatch:** Karmaşıklık ekliyor,
derleme zamanı güvenliğini kaybettiriyor.

## Sonuçlar

**Artılar**
- Yeni teknoloji eklemek: `CacheMonitor` uygulaması yazmak + `getSupportedType()`
  döndürmek. Servis/controller değişmiyor
- `MonitorRegistry` bean listesinden otomatik dolduğu için kayıt adımı yok
- Test edilebilirlik: monitor mock'lanarak servis katmanı izole test edilebilir

**Eksiler / kabul edilen ödünler**
- Arayüz genişledikçe (şu an 13 metot) her uygulama tüm metotları yazmak zorunda.
  Yeni metrik eklendiğinde iki dosya birden değişiyor
- `NOT_APPLICABLE` dönen metotlar boilerplate yaratıyor
- Teknolojiye özgü parametreler arayüze sızabiliyor: `scanKeys(..., String mapName)`
  ve `getKeyTTL(..., String mapName)` imzalarındaki `mapName` yalnızca Hazelcast
  için anlamlı, Redis uygulaması yok sayıyor. Kabul edildi; alternatifi tip başına
  ayrı istek nesnesi ve daha fazla dallanmaydı
