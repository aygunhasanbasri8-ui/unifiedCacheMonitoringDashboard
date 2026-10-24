# UCMD — Bilinen Sınırlar

Bu doküman teslim edilen sürümün bilinen kısıtlarını listeler. Her madde için
**ne**, **neden**, **etkisi** ve **çözüm yolu** verilmiştir.

Amaç dürüst bir teslim: gizlenen sınır, üretimde sürprize dönüşür.

---

## 1. Hazelcast client-only modun kısıtları

**Ne:** `LocalMapStats` ve `PartitionService.isClusterSafe()` Hazelcast Java
client'ında desteklenmiyor. Map istatistikleri (entry sayısı, bellek maliyeti,
get/put sayaçları, gecikme) client API'sinden alınamıyor.

**Neden:** Bu API'ler member (sunucu) tarafında çalışır. Client yalnızca veri erişim
API'lerine sahiptir. UCMD member olarak cluster'a katılamaz — katılsaydı partition
sahipliği alır ve izlediği sistemin veri dağılımını değiştirirdi (ADR-0001 ihlali).

**Etkisi:** Map ve JVM metriklerinin **tamamı** jmx_exporter Prometheus scrape'ine
bağımlı. Bir instance'ın `metrics_url` kolonu boşsa veya scrape erişilemezse
`map-stats`, `operation-stats` ve `resources`'ın JVM bölümü `UNAVAILABLE` döner.
`clusterSafe` her zaman `null`, `safetyStatus` `NOT_APPLICABLE`.

**Çözüm yolu:** jmx_exporter her Hazelcast node'unda çalışmalı ve `metrics_url`
kolonu doldurulmalı (README bölüm 4.4). Hazelcast Management Center REST API'si
alternatif kaynak olabilir; enterprise lisans gerektirir.

---

## 2. Hazelcast entry araması `keySet()` tabanlı

**Ne:** Map içi entry araması (`POST /keys` + `mapName`) `IMap.keySet()` ile tüm
key'leri client'a çekip filtreler. En fazla `ucmd.key-search.hazelcast-max-scan-entries`
(varsayılan 5000) entry taranır.

**Neden:** Redis'in `SCAN` komutunun Hazelcast karşılığı yok. `PagingPredicate` doğru
çözümdür ama key tipi çalışma zamanında bilinmediğinden genel bir predicate yazmak
riskli; ayrıca verimli çalışması için map'te index tanımlı olması gerekir.

**Etkisi:**
- Sonuç her zaman `status: ESTIMATED` — tam tarama garantisi yok
- 5000'den büyük map'lerde arama sonucu eksik olabilir; `hasMore: true` döner
- `keySet()` çağrısı map boyutuyla orantılı ağ trafiği üretir. 5000 sınırı bunu
  kısıtlıyor ama tamamen ortadan kaldırmıyor
- Sayfalama gerçek cursor değil, offset tabanlı; iki çağrı arasında map değişirse
  kayma olabilir

**Çözüm yolu:** İlgili map'lere index tanımlayıp `PagingPredicate` + `Predicates.like`
kullanmak. Key tipinin bilinmesi veya `__key` üzerinden string predicate gerekiyor.
Bu fazda kapsam dışı bırakıldı.

---

## 3. Redis Cluster desteği eksik

**Ne:** `INFO cluster` çıktısındaki `cluster_enabled` okunuyor, ancak `CLUSTER INFO`
ve `CLUSTER NODES` komutları ayrıştırılmıyor.

**Neden:** Yerel geliştirme ortamında Redis Cluster kurulu değil; test edilemeyen
kod yazmak yerine açıkça "hazır değil" demek tercih edildi.

**Etkisi:** Cluster modunda çalışan bir Redis için `POST /{id}/cluster`
`overallStatus: NOT_READY` döner. Slot dağılımı, master/replica topolojisi, migrating
slot bilgisi görünmez. Standalone Redis'te `NOT_APPLICABLE` döner (doğru davranış).

**Çözüm yolu:** `CLUSTER INFO` (durum, slot sayısı, known nodes) ve `CLUSTER NODES`
(node listesi, slot aralıkları, roller) parse edilip mevcut `ClusterInfo` DTO'suna
eşlenebilir. Docker'da 6 node'luk bir cluster ile test edilmelidir.

---

## 4. Partition tablosu ilk bağlantıda boş olabilir

**Ne:** Yeni kurulan Hazelcast client'ta `PartitionService.getPartitions()` çağrısı,
ilk veri işlemi yapılmadan önce sahipsiz partition'lar dönebiliyor.

**Neden:** Hazelcast client partition tablosunu tembel (lazy) doldurur; ilk gerçek
işlemden sonra günceller.

**Etkisi:** Uygulama açılışından hemen sonra `POST /{id}/cluster` çağrılırsa
`unassignedPartitions: 271`, `partitionDistribution: {}`, `distributionStatus:
UNAVAILABLE`, `balanced: null` döner. Bir map işleminden sonra doğru değerler gelir
(doğrulandı: `{hz-analytics:5701: 271}`, `skew: 0.0`, `balanced: true`).

**Etki değerlendirmesi:** Bu bir hata değil, dürüst raporlamadır — kod uydurma değer
üretmek yerine "bilinmiyor" diyor ([ADR-0003](adr/ADR-0003-metricstatus-durum-modeli.md)). Yine de dashboard ilk saniyelerde
"dağılım bilinmiyor" gösterir.

**Çözüm yolu:** `HazelcastConnectionManager` client kurduktan sonra ısıtma (warm-up)
amaçlı hafif bir okuma yapabilir. Eklenmedi — salt-okunur ilkesi gereği yapılacak
çağrının da okuma olması gerekir ve gereksiz trafik yaratır.

---

## 5. `ddl-auto: update` güvenilir değil

**Ne:** `dev` profilinde `spring.jpa.hibernate.ddl-auto: update` kullanılıyor.
Geliştirme sırasında bazı kolon eklemelerinin uygulanmadığı gözlendi.

**Neden:** Hibernate'in `update` modu güvenli bir migration aracı değildir: kolon
tipi daralmalarını, index ve constraint değişikliklerini, kolon silmeyi uygulamaz;
uyguladıklarını da sessizce yapar.

**Etkisi:** Yeni kolonlar (`cluster_name`, `members`, `metrics_url`, `username`,
`password_env_var`) ve `audit_logs` tablosu için SQL'ler elle çalıştırılmalıdır.
Atlanırsa uygulama açılır ama ilgili özellikler sessizce çalışmaz — örneğin
`cluster_name` yoksa Hazelcast instance'ı **400** döner.

**Çözüm yolu:** Flyway veya Liquibase entegrasyonu. Bu fazda kapsam dışı bırakıldı.
Prod profilinde `validate` kullanılıyor — şema eksikse uygulama açılışta durur, bu
sessiz başarısızlıktan iyidir.

---

## 6. Vault entegrasyonu yok

**Ne:** Redis ACL parolaları HashiCorp Vault veya benzeri bir secret manager'dan
değil, environment değişkeninden okunuyor.

**Neden:** Vault kurulumu ayrı bir altyapı gerektirir; bu projenin kapsamı dışında.

**Etkisi:** Parolalar süreç ortamında düz metin olarak bulunur; `/proc/<pid>/environ`
okuyabilen bir kullanıcı erişebilir. Parola rotasyonu uygulama yeniden başlatması
gerektirir.

**Hafifletme:** Parolalar **veritabanında saklanmıyor** — `instances.password_env_var`
yalnızca değişkenin **adını** tutar. Bu, DB yedeklerinin ve DB erişimi olan kişilerin
parolaya ulaşmasını engelliyor.

**Çözüm yolu:** `spring-cloud-vault-config` ile `passwordEnvVar` yerine
`passwordVaultPath` alanı. `RedisConnectionManager.resolvePassword()` tek değişim
noktası — soyutlama hazır.

---

## 7. Snapshot downsampling basit stride örneklemesi

**Ne:** `POST /{id}/history` nokta sayısı `maxPoints`'i aştığında `total / maxPoints`
adımıyla her n'inci kaydı seçer. Ortalama, min/max veya başka bir toplama yapılmaz.

**Neden:** Bilinçli sadelik. Toplama fonksiyonu seçmek (ortalama mı, p95 mi, max mı)
metriğe göre değişir ve bu fazda gereksiz karmaşıklık getirirdi.

**Etkisi:** Uzun aralıklarda ani sivri uçlar (spike) örneklem dışında kalabilir.
Örneğin 7 günlük geçmiş 100 noktaya indirgenirken 30 saniyelik bir CPU sivri ucu
kaybolabilir. `resolution: "DOWNSAMPLED"` alanı bunu açıkça bildiriyor.

**Çözüm yolu:** Metrik tipine göre toplama (CPU için max, sayaçlar için son değer,
oranlar için ortalama) veya LTTB (Largest-Triangle-Three-Buckets) algoritması.

---

## 8. `/v1/health/collection` kimlik doğrulama istemiyor

**Ne:** Toplama sağlığı ucu `SecurityConfig`'de `/v1/health/**` muafiyeti kapsamında;
token olmadan çağrılabiliyor.

**Neden:** Bilinçli karar. Operasyonel sağlık bilgisi izleme sistemlerinin (Prometheus
blackbox exporter, load balancer health check) token yönetmeden erişebilmesi gereken
bir uçtur.

**Etkisi:** Yanıt instance **adlarını** (`unavailableInstances`) ve sayılarını
sızdırır. Key adı, metrik değeri veya kullanıcı bilgisi içermez.

**Çözüm yolu:** Instance adları hassas kabul edilirse yanıttan çıkarılıp yalnızca
sayı döndürülebilir, ya da uç `/actuator` gibi ayrı bir porta taşınabilir.

---

## 9. Export / download ve value görüntüleme yok

**Ne:** Metrik verisini CSV/Excel dışa aktarma ve cache'teki key **değerlerini**
görüntüleme özellikleri yok.

**Neden:** Proje kapsamı bu iki maddeyi açıkça dışarıda bırakıyor. Value
görüntüleme ayrıca ADR-0001 ile de çelişir: değer okumak kart numarası, komisyon
oranı gibi veriyi doğrudan açığa çıkarır.

**Etkisi:** Operatör key adını ve boyutunu görür, içeriğini göremez. Rapor almak için
Prometheus/Grafana veya doğrudan DB sorgusu gerekir.

**Çözüm yolu:** Export için `/history` yanıtı istemci tarafında CSV'ye çevrilebilir.
Value görüntüleme eklenecekse maskeleme, ek yetki seviyesi ve zorunlu denetim kaydı
tasarlanmalıdır.

---

## 10. Otomatik test yok

**Ne:** Bu fazda unit veya integration test yazılmadı. Depoda yalnızca Spring Boot'un
ürettiği boş `UcmdApplicationTests` bulunuyor.

**Neden:** Testler bir sonraki faza planlandı.

**Etkisi:** Regresyon koruması yok. Tüm doğrulamalar canlı sistem üzerinde elle
yapıldı (her fazın doğrulama tablosu). `mvn clean compile` geçiyor ancak
`mvn test` anlamlı bir kapsam ölçmüyor.

**En kırılgan noktalar (öncelikli test hedefleri):**
- `PrometheusTextParser` — etiket ayrıştırma, `tag0` escape çözümü, `prefix` ayrımı.
  Gerçek scrape çıktısıyla 20 iddia elle doğrulandı, testleştirilmeli
- `RedisMonitor.parseKeyspace` — çok DB'li `INFO keyspace` ayrıştırması
- `ProtectedMonitorExecutor` — cache/STALE/TIMEOUT geçişleri
- `KeyMasker` — maskeleme kuralı ve sıralama etkileşimi
- Hibrit 404/UNAVAILABLE politikası (ADR-0005)

**Çözüm yolu:** Parser ve saf mantık için unit test; monitor katmanı için Testcontainers
ile Redis + Hazelcast integration testi.

---

## 11. Hazelcast `hitRatio` 1'i aşabiliyor ⚠️

**Ne:** `MapStatsInfo.MapDetail.hitRatio` ve `POST /{id}/efficiency` yanıtı
matematiksel olarak imkânsız değerler üretebiliyor. Ölçülen örnek:

```
node 9404 (ucmd-merchants):  hits = 659,  getcount = 900
node 9405 (ucmd-merchants):  hits = 710,  getcount = 0
                    toplam:  hits = 1369, getcount = 900   →  hitRatio = 1.52
```

`/efficiency` ucunda ise `keyspaceMisses` `max(0, gets - hits)` ile hesaplandığından
**0** çıkıyor ve `hitRatio` **1.0** (yani "%100 isabet") görünüyor.

**Neden:** Hazelcast `hits` sayacını **entry sahibi** member'da, `getcount` sayacını
**isteği alan** member'da tutuyor. UCMD client'ı unisocket modda (ADR-0009) tek
member'a bağlandığı için `getcount` orada toplanıyor, `hits` ise partition sahipliğine
göre tüm node'lara dağılıyor. İki sayacın node'lar arası toplamını bölmek geçersiz.

**Etkisi:** Dashboard'da yanıltıcı hit oranı. ADR-0003'teki "yanlış değer üretme" kuralının
ihlali. Redis tarafı etkilenmiyor — orada `keyspace_hits`/`keyspace_misses` tek
süreçte tutulan tutarlı sayaçlardır.

**Çözüm yolu (onay bekliyor):** Oranı node bazında hesaplayıp yalnızca `getcount > 0`
olan node'ları dikkate almak; veya `hits > getcount` durumunda oranı `null` bırakıp
statüyü `UNAVAILABLE` yapmak. İkincisi ADR-0003'e daha uygun.

**Çözüldü (branch `claude`):** `HazelcastMonitor.getMapStats` artık `MapDetail.hitRatio`
alanını yalnızca veri kendi içinde tutarlıysa (`getcount > 0` ve `hits <= getcount`)
dolduruyor; aksi hâlde `null` bırakıyor. `getCacheEfficiency` zaten `NOT_APPLICABLE`
dönüyordu, dokunulmadı.

---

## 12. `systemCpuPercent` yüzde değil ⚠️

**Ne:** `ResourceStats.systemCpuPercent` 100'ü aşan değerler döndürüyor. Ölçülen ham
değerler: node 9404 → `2.717`, node 9405 → `2.470`. Ortalamanın 100 ile çarpımı
**%259** veriyor.

**Neden:** Hazelcast'in `os.systemcpuload` metriği bu container ortamında 0–1
aralığında bir oran döndürmüyor. `os.processcpuload` (0.10) doğru davranıyor;
`cpuUsagePercent` alanı bu yüzden doğru.

**Etkisi:** Alan adı `...Percent` olduğu için dashboard'da yüzde olarak gösterilirse
anlamsız değer çıkar. `nonHeapMaxBytes` alanında da benzer bir sorun var: her node
`-1` (limitsiz) döndürüyor, toplamları **-2** oluyor — sentinel değerler toplanamaz.

**Çözüm yolu (onay bekliyor):** `systemcpuload` için `min(1.0, v)` kırpması veya
`v > 1` durumunda `null` + `UNAVAILABLE`. `nonHeapMaxBytes` için negatif değer
görülürse `null` dönmek.

**Çözüldü (branch `claude`):** `systemCpuPercent` için `v < 0 || v > 1` kontrolü
zaten vardı (metrik `null` oluyor). Eksik olan `nonHeapMaxBytes`/`nonHeapUsedBytes`
ve heap `max` alanlarına negatif-sentinel koruması eklendi (`nonNegLong` yardımcısı:
negatif toplam → `null`).

**İlgili:** `threadCount` node'lar arası **toplam** (124), `peakThreadCount` ise
node'lar arası **maksimum** (98). Karışık semantik nedeniyle `peak < current`
görünüyor; ikisi de teknik olarak doğru ama yan yana kafa karıştırıcı.

---

## 13. Ham key adı log dosyasına düşebiliyor ⚠️

**Ne:** `KeyAnalysisServiceImpl.getKeyTTL` cache anahtarı olarak
`"key-ttl:" + mapName + ":" + key` kullanıyor — ham key operasyon adının içinde.
`ProtectedMonitorExecutor` bu adı iki yerde logluyor:

```java
logger.warn("Metric call '{}' failed for {}: {}", operation, ...);
logger.debug("Serving STALE value for {} / {}", instance.getName(), operation);
```

**Neden:** Cache anahtarının benzersiz olması gerekiyor (aksi halde farklı key'lerin
TTL sorguları birbirinin cache'lenmiş yanıtını alır). Anahtar ham key içerdiği için
operasyon adı da ham key içeriyor.

**Etkisi:** Hedef sistem erişilemez olduğunda veya STALE değer sunulduğunda
maskelenmemiş key adı log dosyasına yazılıyor. Bu, ADR-0010'da tanımlanan
"log'a yazılan tüm key adları maskelenir" kuralının ihlali. Normal çalışmada
(başarılı çağrı) log üretilmiyor, yalnızca hata/bayat yollarında.

**Çözüm yolu (onay bekliyor):** İlgili iki log satırında
`keyMasker.maskForLog(operation)` kullanmak. Cache anahtarı ham kalmalı — yalnızca
log maskelenmeli.

**Zaten çözülmüş (kod incelemesi, branch `claude`):** `executeCachedMetric`
imzası `(instance, cacheSuffix, logLabel, call)` biçiminde ayrılmış durumda.
`KeyAnalysisServiceImpl.getKeyTTL` ham key'i yalnızca `cacheSuffix` olarak veriyor
(cache anahtarı için), `logLabel` olarak sabit `"key-ttl"` geçiyor. `ProtectedMonitorExecutor`
tüm log satırlarında `logLabel`'ı kullanıyor — ham key log'a düşmüyor. Bu madde
güncel değil.

## TLS desteği doğrulanmadı

`InstanceRef.tlsEnabled` işaretlendiğinde `RedisConnectionManager` bağlantıyı
Lettuce'un `withSsl(true)` yolundan kurar. Bu yol **hiç çalıştırılmadı** —
geliştirme ortamındaki Redis container'ları TLS'siz yapılandırılmıştır.

TLS güvenlik kontrol listesinde var ama zorunlu kapsamda yer almıyor
([ADR-0014](adr/ADR-0014-instance-bazli-tls.md)). Kod, TLS'li bir ortam sağlandığında yapılandırmayla açılabilmesi için
hazır bırakılmıştır.

Prod ortamında etkinleştirmeden önce:

1. Redis tarafında `tls-port` açık, sertifika ve CA yapılandırılmış olmalı.
2. Redis'in sertifikası kurum CA'sıyla imzalıysa CA sertifikası JVM truststore'una
   eklenmelidir (`keytool -importcert`). Aksi halde el sıkışma başarısız olur ve
   bağlantı `UNAVAILABLE` döner.
3. Sertifika doğrulaması bilinçli olarak kapatılmamıştır; `verifyPeer(false)` gibi
   bir geçici çözüm eklenmemelidir.

Hazelcast TLS'i Enterprise lisans gerektirdiğinden kapsam dışıdır; `tlsEnabled`
Hazelcast instance'larında dikkate alınmaz.

---

## Özet

| # | Sınır | Kategori | Durum |
|---|---|---|---|
| 1 | Hazelcast client-only kısıtları | Teknoloji | Prometheus ile aşıldı |
| 2 | `keySet()` tabanlı entry araması | Tasarım ödünü | `ESTIMATED` ile bildiriliyor |
| 3 | Redis Cluster desteği | Kapsam dışı | `NOT_READY` dönüyor |
| 4 | Partition tablosu gecikmesi | Davranış | Dürüst raporlanıyor |
| 5 | `ddl-auto: update` | Operasyon | SQL elle çalıştırılıyor |
| 6 | Vault yok | Kapsam dışı | Parola DB'de değil |
| 7 | Basit downsampling | Tasarım ödünü | `resolution` ile bildiriliyor |
| 8 | `/health/collection` açık | Bilinçli karar | Hassas veri yok |
| 9 | Export / value yok | Kapsam dışı | Proje kapsamı |
| 10 | Test yok | Teknik borç | Güncel değil — 109 test var (ADR-0013) |
| 11 | `hitRatio` > 1 | **Hata** | ✅ Çözüldü (branch `claude`) — tutarlıysa oran, değilse `null` |
| 12 | `systemCpuPercent` yüzde değil / `nonHeapMax` = -2 | **Hata** | ✅ Çözüldü (branch `claude`) — negatif-sentinel koruması |
| 13 | Log'da ham key | **Güvenlik** | ✅ Kodda zaten çözülmüş — `cacheSuffix`/`logLabel` ayrımı; madde güncel değil |
