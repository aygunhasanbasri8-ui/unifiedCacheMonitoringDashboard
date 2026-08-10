# ADR-0009: Instance bazlı Hazelcast client havuzu (multi-cluster)

**Durum:** Kabul edildi
**Tarih:** 2026-08-06

## Bağlam

İlk tasarımda `HazelcastConfig` **tek bir** `HazelcastInstance` bean'i üretiyordu.
Cluster adı ve member listesi global yapılandırmadan (`hazelcast.cluster-name`,
`hazelcast.members`) okunuyordu.

`HazelcastMonitor`'ın hiçbir metodu kendisine verilen `InstanceRef` parametresini
kullanmıyordu — hepsi enjekte edilen tek client üzerinden çalışıyordu.

Sonuç: veritabanına iki farklı Hazelcast instance'ı kaydedilse bile **ikisi de aynı
cluster'ın verisini** dönüyordu. Multi-cluster izleme fiilen çalışmıyordu ve bu
sessiz bir yanlışlıktı — API doğru görünüyor, veri yanlış geliyordu.

Redis tarafında `RedisConnectionManager` doğru kalıbı zaten uyguluyordu.

## Karar

`HazelcastConfig` silindi. Yerine `HazelcastConnectionManager` (`@Component`) geldi:

- **Anahtar:** `InstanceRef.connectionKey()` → `"{clusterName}|{members}"`
- **Havuz:** `ConcurrentHashMap<String, HazelcastInstance>`
- **Canlılık:** `getLifecycleService().isRunning()`; ölü client havuzdan çıkarılıp
  yeniden kurulur
- **Bağlantı kurulamazsa:** `null` döner, exception fırlatmaz → çağıran taraf
  `UNAVAILABLE` set eder (ADR-0005 uyumlu)
- **`clusterName` boşsa:** `IllegalArgumentException` → **400**. Yapılandırma hatası
  gizlenmemeli
- **Yaşam döngüsü:** `@PreDestroy` tüm client'ları kapatır, adedi loglar

Client ayarları:

| Ayar | Değer | Gerekçe |
|---|---|---|
| `smartRouting` | `false` (unisocket) | Host makine, container'ların iç DNS adlarını çözemiyor |
| `asyncStart` | `true` | Hazelcast kapalıyken de UCMD ayağa kalkmalı |
| `reconnectMode` | `ASYNC` | Bağlantı koptuğunda çağrılar bloklanmasın |
| `clusterConnectTimeoutMillis` | 5000 | — |
| `initialBackoff / maxBackoff / multiplier` | 1000 / 30000 / 1.5 | Çöken cluster'a yeniden bağlanma denemelerini seyrelt |

`InstanceRef`'e üç alan eklendi: `clusterName`, `members`, `metricsUrl`.
`HazelcastMonitor`'ın her metodu client'ı manager'dan çözer.

Yapılandırma değiştiğinde (`updateInstance`, `deleteInstance`) ilgili client
`evict(instance)` ile kapatılır.

## Gerekçe

**Neden `clusterName|members` anahtarı, `instanceId` değil?** Aynı cluster'a işaret
eden iki kayıt varsa tek client paylaşılır. Cluster kimliği bağlantının gerçek
kimliğidir, DB satır numarası değil.

**Neden `smartRouting=false`?** Smart routing'de client, cluster'dan aldığı member
adreslerine doğrudan bağlanır. Docker'da member'lar kendilerini `hazelcast:5701`
diye tanıtıyor; host makineden bu ad çözülemiyor. Unisocket modda client yalnızca
verilen adresi kullanır. Ödünü: tüm istekler tek member üzerinden gider, ekstra hop
oluşur. İzleme yükü düşük olduğu için kabul edilebilir.

Bu ödünün bir yan etkisi var: Hazelcast `getcount` sayacını isteği alan member'da
tutuyor, `hits` sayacını ise entry sahibinde. Unisocket modda `getcount` tek member'da
toplanıyor — bkz. `docs/known-limitations.md` madde 11.

**Neden bağlantı hatasında `null`, exception değil?** Exception fırlatsaydı koruma
katmanı bunu hata sayıp circuit breaker'ı açardı; oysa "henüz bağlanmadı" normal bir
durum (asyncStart). `null` + `UNAVAILABLE` doğru sinyal.

**Neden `clusterName` boşluğunda exception?** Bu bir veri hatasıdır, geçici erişim
sorunu değil. `UNAVAILABLE` dönmek operatörün eksik yapılandırmayı fark etmesini
geciktirirdi.

## Sonuçlar

**Artılar**
- Birden çok Hazelcast cluster gerçekten izlenebiliyor. Doğrulama: `dev` cluster
  `nodeCount: 2`, `analytics` cluster `nodeCount: 1`, partition dağılımları farklı
- Bir cluster'ın çökmesi diğerini etkilemiyor (test edildi: `docker stop hz-analytics`
  → yalnızca o instance `UNAVAILABLE`)
- Client sızıntısı yok; kapanışta adet loglanıyor

**Eksiler / kabul edilen ödünler**
- Her cluster için ayrı client = ayrı thread havuzu ve soket. Çok sayıda cluster'da
  kaynak tüketimi artar
- Unisocket mod nedeniyle bazı per-member metrikler tek member'dan geliyor
- `metrics_url` kolonu boş bırakılırsa JVM/map/operation metrikleri `UNAVAILABLE`
  döner; kurulum adımı atlanırsa sessiz veri kaybı gibi görünür (README'de belirtildi)
- Şema değişikliği gerektirdi: `instances` tablosuna üç kolon (migration elle)
