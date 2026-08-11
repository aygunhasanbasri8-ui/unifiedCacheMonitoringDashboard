# ADR-0014 — Instance bazlı TLS

**Durum:** Kabul edildi (doğrulanmadı)
**Tarih:** 2026-08-11

## Bağlam

Rehber §9 hem Redis hem Hazelcast için TLS'i güvenlik kontrol listesinde sayıyor.
§11'in zorunlu kapsamında yer almıyor. Geliştirme ortamındaki Redis container'ları
TLS'siz yapılandırılmış; TLS'li bir ortam kurmak sertifika üretimi ve JVM truststore
yapılandırması gerektiriyor.

## Karar

TLS **instance bazlı** yapılandırılır: `InstanceRef.tlsEnabled`. Kurum ortamında bazı
Redis'lerin TLS'li bazılarının olmaması mümkündür; global bir ayar bunu karşılamaz.

`RedisConnectionManager` bağlantı cache anahtarına TLS bayrağını dahil eder
(`host:port:username:tls`) — aynı sunucuya TLS'li ve TLS'siz bağlantı ayrı tutulur.

**Sertifika doğrulaması kapatılmaz.** `verifyPeer(false)` veya benzeri bir gevşetme
eklenmemiştir; doğrulama JVM truststore'una bırakılır.

Hazelcast TLS'i Enterprise lisans gerektirdiğinden kapsam dışıdır; `tlsEnabled`
Hazelcast instance'larında dikkate alınmaz.

## Sonuçlar

- **Bu yol hiç çalıştırılmadı.** `docs/known-limitations.md`'de belgelendi.
- Kurum CA'sıyla imzalı sertifika kullanılıyorsa CA sertifikası JVM truststore'una
  eklenmelidir (`keytool -importcert`), aksi halde el sıkışma başarısız olur ve
  bağlantı `UNAVAILABLE` döner.
- Şema değişikliği: `instances.tls_enabled boolean NOT NULL DEFAULT false`

## Değerlendirilen alternatifler

**TLS'i tamamen ertelemek** — §9'un açık maddesi olduğu için kod hazır bırakıldı;
DevOps TLS'li ortam sağladığında yalnızca yapılandırma gerekir.

**Docker Redis'e sertifika kurup test etmek** — yarım günlük iş, ve üretilen
sertifika kurum ortamındakiyle aynı zinciri temsil etmeyeceği için doğrulama değeri
sınırlı kalırdı.