# ADR-0013 — Test stratejisi

**Durum:** Kabul edildi
**Tarih:** 2026-08-11

## Bağlam

Proje kapsamı unit ve integration testlerini zorunlu sayıyor. Test
altyapısı kurulurken üç ayrı karar gerekti: ayrıştırıcıların private olması,
integration testlerin gerçek bağımlılık ihtiyacı, ve secret'ların env değişkeninden
okunması.

## Karar

**Ayrıştırıcılara reflection ile erişilir.** `RedisMonitor.infoLong/parseKeyspace/
calculateCpuPercent/failureStatus` ve `HazelcastMonitor.applyPartitionMetrics`
private kalır; testler `ReflectionTestUtils.invokeMethod` kullanır. Test uğruna
üretim kodunda görünürlük gevşetilmez.

**Redis için Testcontainers, Hazelcast için gömülü member.** Redis integration
testleri gerçek bir `redis:7-alpine` container'ı kullanır — ACL, NOPERM ve bağlantı
kaybı senaryoları ancak gerçek sunucuyla üretilebilir. Hazelcast'te
`com.hazelcast:hazelcast` tam artifact kullanıldığından member'lar aynı JVM'de
ayağa kaldırılır, Docker gerekmez.

**Docker yoksa atlanır, kırılmaz.** `@Testcontainers(disabledWithoutDocker = true)`.
Teslim ortamında Docker olmayabilir; `mvn test`'in kırmızı dönmesi yerine ilgili
sınıflar atlanır.

**Secret'lar surefire `environmentVariables` bloğundan gelir.** `JwtTokenProvider`
ve `UserServiceImpl` secret'ı `System.getenv` ile okur, Spring property değil. Bu
bir property'ye çevrilmedi — static API'yi çağıran her yeri etkilerdi. Bunun bedeli
IntelliJ'den test çalıştırırken env değişkenlerinin elle tanımlanmasıdır (README §10).

## Sonuçlar

- 109 test; teslim ortamında Docker yoksa Redis integration testleri atlanır
- Reflection kullanan testler metot adı değişikliğinde derleme değil çalışma
  zamanı hatası verir
- Testler PostgreSQL'e bağımlı değil, H2 in-memory ile çalışır

## Değerlendirilen alternatifler

**Ayrıştırıcıları package-private yapmak** — testler daha temiz olurdu, ama sırf
test için üretim API yüzeyini genişletmek istenmedi.

**Embedded Redis kütüphanesi** — Docker gereksinimini kaldırırdı, ancak ACL desteği
gerçek Redis sürümüyle birebir örtüşmüyor; §10'un istediği ACL ve permission-denied
senaryoları güvenilir üretilemezdi.