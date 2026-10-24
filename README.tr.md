# UCMD — Unified Cache Monitoring Dashboard

[English](README.md) | Türkçe

[![CI](https://github.com/aygunhasanbasri8-ui/unifiedCacheMonitoringDashboard/actions/workflows/ci.yml/badge.svg)](https://github.com/aygunhasanbasri8-ui/unifiedCacheMonitoringDashboard/actions/workflows/ci.yml)

## 1. Proje Özeti

UCMD, dağıtık ortamdaki Redis ve Hazelcast instance'larını tek merkezden izleyen
**salt-okunur** bir monitoring aracıdır. Farklı takımların farklı araçlarla (redis-cli,
Hazelcast Management Center, Grafana) baktığı cache sistemlerini tek API ve tek panel
altında toplar.

Çözdüğü problem: bir üretim olayında "cache dolu mu, hangi key'ler şişiyor, replica
gerilerde mi, hangi node yük altında" sorularının cevabı bugün birkaç ayrı araca
bakmayı gerektiriyor. UCMD bu bilgileri tek yerde, tutarlı bir durum modeliyle sunar.

Kritik tasarım ilkesi: **izlenen sistemi asla bozmamak**. Hiçbir yazma komutu
çalıştırılmaz, `KEYS *` kullanılmaz, her çağrı rate limit / bulkhead / circuit breaker /
timeout katmanından geçer, erişilemeyen metrik için uydurma değer değil `UNAVAILABLE`
döner.

---

## 2. Mimari

```
┌────────────────┐
│ React Frontend │
└───────┬────────┘
        │ HTTPS + JWT (Bearer)
┌───────▼─────────────────────────────────────────────────────────┐
│                     REST API (Spring Boot)                       │
│  AuthInterceptor → @RequireRole → Controller                     │
│  /v1/instances/**   /v1/health/**   /v1/admin/**   /v1/audit     │
└───────┬─────────────────────────────────────────────────────────┘
        │
┌───────▼────────────────┐      ┌──────────────────────────────┐
│  MonitoringService     │      │  KeyAnalysisService          │
│  HistoryService        │      │  AuditService / UserService  │
└───────┬────────────────┘      └──────────────────────────────┘
        │
┌───────▼─────────────────────────────────────────────────────────┐
│              ProtectedMonitorExecutor  (ADR-0006)                │
│   cache → rate limiter → bulkhead → circuit breaker → timeout    │
│   executeCachedMetric()  |  executeExpensive()                   │
└───────┬─────────────────────────────────────────────────────────┘
        │
┌───────▼────────────────┐
│  CacheMonitor (arayüz) │  ← MonitorRegistry tipe göre seçer
└───┬────────────────┬───┘
    │                │
┌───▼──────────┐  ┌──▼───────────────┐
│ RedisMonitor │  │ HazelcastMonitor │
│  (Lettuce)   │  │  (HZ Client 5.3) │
└───┬──────────┘  └──┬────────────┬──┘
    │                │            │
    │ PING/INFO/     │ size()     │ HTTP GET
    │ SCAN/TYPE/     │ getCluster │
    │ PTTL/MEMORY    │ Partition  │
    ▼                ▼            ▼
┌─────────┐   ┌─────────────┐  ┌──────────────────┐
│  Redis  │   │  Hazelcast  │  │ jmx_exporter     │
│ 6379/80 │   │ dev(2 node) │  │ :9404/9405/9406  │
│         │   │ analytics   │  │ /metrics         │
│         │   │ (1 node)    │  │                  │
└─────────┘   └─────────────┘  └──────────────────┘

Yan dallar:
  • PostgreSQL  → instances, metric_snapshots, audit_logs, users
  • Caffeine    → 10 sn TTL metrik cache + 30 dk STALE fallback penceresi
  • Micrometer  → /actuator/prometheus (UCMD'nin kendi öz-izleme metrikleri)
  • SnapshotScheduler → 30 sn'de bir tüm instance'lardan snapshot toplar
```

**Bağlantı havuzları:** `RedisConnectionManager` (`host:port:username` bazlı),
`HazelcastConnectionManager` (`clusterName|members` bazlı). İkisi de bağlantıları
önbelleğe alır, ölü bağlantıyı yeniden kurar, `@PreDestroy` ile kapatır.

---

## 3. Teknoloji Yığını

| Bileşen | Sürüm | Not |
|---|---|---|
| Java | 21 | Virtual Threads aktif (`spring.threads.virtual.enabled`) |
| Spring Boot | 3.5.16 | Web, Data JPA, Actuator, AOP |
| PostgreSQL | 15 | `ucmd_db` |
| H2 | test | test profili in-memory DB |
| Testcontainers | Spring Boot yönetimli | integration testlerde gerçek Redis |
| Lettuce | Spring Boot yönetimli | `RedisClient`, `ByteArrayCodec` |
| Hazelcast Client | 5.3.8 | unisocket (`smartRouting=false`) |
| resilience4j | 2.2.0 | rate limiter, bulkhead, circuit breaker |
| Caffeine | Spring Boot yönetimli | kısa TTL metrik cache |
| Micrometer + Prometheus | Spring Boot yönetimli | `/actuator/prometheus` |
| springdoc-openapi | 2.8.6 | Swagger UI, iki grup |
| jjwt | 0.12.6 | HMAC-SHA256 |
| BCrypt | spring-security-crypto | parola özeti |
| Frontend | React 19, Vite, React Router, Recharts, Axios | `frontend/` altında; Docker'da nginx ile sunulur |

> **Not:** springdoc 2.6.0 Spring Framework 6.2 ile ikili uyumsuzdur
> (`NoSuchMethodError: ControllerAdviceBean.<init>`). 2.8.6 kullanılmalıdır.

---

## 4. Kurulum

### 4.1 Ön koşullar

- JDK 21
- Maven 3.9+ (veya depodaki `mvnw`)
- Docker + Docker Compose
- PostgreSQL 15 (compose ile geliyor)

> Her şeyi Docker'dan çalıştıracaksanız (bölüm 4.6) bunlardan **yalnızca Docker**
> gerekir; JDK, Maven, Node ve PostgreSQL imajların içindedir.

### 4.2 `.env` değişkenleri

Kök dizinde `.env` dosyası oluşturun:

| Değişken | Zorunlu | Açıklama |
|---|---|---|
| `JWT_SECRET` | ✅ | En az 32 karakter. Kısa olursa uygulama açılışta durur |
| `JWT_EXPIRATION_HOURS` | — | Varsayılan 24 |
| `UCMD_BOOTSTRAP_USERNAME` | ✅ (dev) | İlk açılışta oluşturulan tek yönetici hesabının adı |
| `UCMD_BOOTSTRAP_PASSWORD` | ✅ (dev) | Aynı hesabın parolası |
| `UCMD_BOOTSTRAP_ROLE` | — | Varsayılan `ADMIN` |
| `SPRING_DATASOURCE_URL` | — | Varsayılan `jdbc:postgresql://localhost:5432/ucmd_db` |
| `SPRING_DATASOURCE_USERNAME` | ✅ | Varsayılanı yok. Docker Compose'ta `POSTGRES_USER`'dan alınır |
| `SPRING_DATASOURCE_PASSWORD` | ✅ | Varsayılanı yok. Docker Compose'ta `POSTGRES_PASSWORD`'den alınır |
| `POSTGRES_USER`, `POSTGRES_PASSWORD` | ✅ (Compose) | `postgres` ve `ucmd-backend` servislerinin ortak kimlik bilgisi |
| `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD` | ✅ (Compose) | Grafana yönetici hesabı |
| `REDIS_CACHE_PASSWORD` | ✅ (Compose) | `redis-2` (6380) parolası |
| `UCMD_CORS_ALLOWED_ORIGINS` | — | Varsayılan `http://localhost:5173,http://localhost:5174,http://localhost:3000` |
| `APP_ENVIRONMENT` | — | `production` ise bootstrap yönetici hesabı oluşturulmaz |
| `<INSTANCE_PASSWORD_ENV>` | — | Redis ACL parolası; adı `instances.password_env_var` kolonunda tutulur |

Tüm Compose değişkenleri için örnek dosya: [`.env.example`](.env.example).

> Parolalar **veritabanında saklanmaz**. `InstanceRef.passwordEnvVar` yalnızca
> hangi environment değişkeninden okunacağının **adını** taşır. Sunucu tarafı ikinci savunma hattı
> için bkz. [`docs/redis-acl-setup.md`](docs/redis-acl-setup.md); parola yönetiminin sınırları
> [`docs/known-limitations.md`](docs/known-limitations.md) madde 6'da.

> Kodda gömülü varsayılan kullanıcı adı veya parola **yoktur**. Uygulama ilk
> açılışta yalnızca `UCMD_BOOTSTRAP_*` ile tanımlanan tek yönetici hesabını
> oluşturur; diğer kullanıcılar bu hesapla UI'daki kullanıcı yönetimi
> ekranından açılır. Değişkenler tanımsızsa uygulama açılışta durur.
>
> Hesap veritabanında zaten varsa bu değerler yok sayılır — parolayı
> değiştirmek için hesabı silip uygulamayı yeniden başlatın.

### 4.3 Altyapıyı ayağa kaldır

Compose dosyası `.env` değişkenlerini zorunlu tutar; önce `cp .env.example .env` ile oluşturup
değerleri doldurun.

```bash
docker compose up -d
```

Gelen servisler:

| Servis | Port | Açıklama |
|---|---|---|
| `redis` | 6379 | 256 MB, `allkeys-lru` |
| `redis-2` | 6380 | 128 MB, `allkeys-lru` |
| `hazelcast` + `hazelcast-2` | 5701, 5702 | `dev` cluster, 2 node |
| `hz-analytics` | 5703 | `analytics` cluster, 1 node |
| jmx_exporter | 9404, 9405, 9406 | Hazelcast node'larının Prometheus scrape'i |
| `postgres` | 5433 | `ucmd_db` (container içi 5432) |
| `prometheus` | 9090 | — |
| `grafana` | 3001 | Hesap `.env`'deki `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` |
| `ucmd-backend` | 8080 | Spring Boot API (opsiyonel — bkz. bölüm 4.6) |
| `ucmd-frontend` | 3000 | React + nginx (opsiyonel — bkz. bölüm 4.6) |

> `docker compose up -d` **tüm** servisleri başlatır. Yalnızca altyapıyı isterseniz
> servis adlarını açıkça verin:
>
> ```bash
> docker compose up -d redis redis-2 hazelcast hazelcast-2 hz-analytics postgres prometheus grafana
> ```

Doğrulama:

```bash
docker logs hazelcast 2>&1 | grep -A3 "Members {"
```

```bash
docker logs hz-analytics 2>&1 | grep -A2 "Members {"
```

```bash
curl -s localhost:9406/metrics | grep jvm_memory_bytes_used | head -3
```

`hazelcast` için `size:2`, `hz-analytics` için `size:1` görülmelidir.

### 4.4 Veritabanı

Şemayı Flyway yönetiyor (`src/main/resources/db/migration`): temiz bir veritabanında
`V1__initial_schema.sql` açılışta çalışıyor, Hibernate yalnızca doğruluyor
(`ddl-auto: validate`), uyuşmazlıkta uygulama sessizce bozulmak yerine açılmıyor. Elle ya da
eski `ddl-auto: update` ile kurulmuş mevcut bir veritabanı ilk açılışta sürüm 1 olarak
işaretleniyor (`baseline-on-migrate`) ve olduğu gibi bırakılıyor. Böyle bir veritabanı aşağıdaki
kolonlardan önceyse açılıştaki doğrulama eksik kolonu söylüyor; bunu bir kez çalıştırın:

```sql
-- ── Hazelcast multi-cluster ──
ALTER TABLE instances
  ADD COLUMN IF NOT EXISTS cluster_name VARCHAR(255),
  ADD COLUMN IF NOT EXISTS members      VARCHAR(1000),
  ADD COLUMN IF NOT EXISTS metrics_url  VARCHAR(1000);

-- ── Denetim kaydı ──
CREATE TABLE IF NOT EXISTS audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    action      VARCHAR(64)  NOT NULL,
    detail      VARCHAR(1000),
    username    VARCHAR(128),
    timestamp   TIMESTAMP    NOT NULL,
    result      VARCHAR(32),
    ip_address  VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON audit_logs (timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_username  ON audit_logs (username);
CREATE INDEX IF NOT EXISTS idx_audit_action    ON audit_logs (action);

-- ── Redis ACL credential desteği ──
ALTER TABLE instances
  ADD COLUMN IF NOT EXISTS username          VARCHAR(255),
  ADD COLUMN IF NOT EXISTS password_env_var  VARCHAR(255);
```

Varsayılan instance kayıtları (`Redis-prod`, `Redis-cache`, `Hazelcast-dev`, `Hazelcast-analytics`),
`instances` tablosu boşken uygulama tarafından `ucmd.seed.*` ayarlarından oluşturulur; elle `INSERT`
gerekmez.

### Veritabanı şeması

Şema kaynağı `src/main/resources/db/migration/V1__initial_schema.sql`; temiz bir ortamda
elle çalıştırılacak bir şey yok. Bu dosya yalnızca UCMD'nin kendi tablolarını (`instances`, `users`,
`metric_snapshots`, `audit_logs`, `alert_events`) oluşturur. `sim/` altındaki simülasyon
scriptlerinin ürettiği tablolar buraya dahil değildir.

### 4.5 Uygulamayı çalıştır

```bash
mvn spring-boot:run
```

Uygulama `.env` dosyasını kendisi okumaz; değişkenlerin (özellikle `JWT_SECRET`, `UCMD_BOOTSTRAP_*`,
`SPRING_DATASOURCE_USERNAME` ve `SPRING_DATASOURCE_PASSWORD`) ortamda tanımlı olması gerekir.
Windows'ta `.env` yükleyip başlatmak için depodaki `run.ps1` kullanılabilir.

- API kökü: `http://localhost:8080/api`
- Swagger UI: `http://localhost:8080/api/swagger-ui.html`

Frontend'i ayrı çalıştırmak için:

```bash
cd frontend && npm install && npm run dev
```

Vite `http://localhost:5173` üzerinde açılır ve API'ye doğrudan
`http://localhost:8080/api` adresinden gider (CORS izni `CorsConfig`'te tanımlıdır).

### 4.6 Her şeyi Docker ile çalıştırma

Backend ve frontend de compose'a dahildir; host'a JDK veya Node kurmadan tüm
sistem ayağa kalkar. **Tek ön koşul Docker'dır.**

Sıfırdan bir makinede:

```bash
git clone https://github.com/aygunhasanbasri8-ui/unifiedCacheMonitoringDashboard.git
```

```bash
cd unifiedCacheMonitoringDashboard && cp .env.example .env
```

`.env` git'te tutulmaz, her makinede oluşturulmalıdır. İçindeki `JWT_SECRET`
(en az 32 karakter) ile yönetici hesabının adı ve parolasını doldurun, sonra:

```bash
docker compose up -d --build
```

İlk build Maven bağımlılıklarını indirdiği için 5–10 dakika sürer; sonraki
build'ler katman cache'inden saniyeler içinde tamamlanır.

**Makine gereksinimleri**

- Docker'a en az ~4 GB RAM ayrılmalı — backend ve 3 Hazelcast node'u ayrı
  JVM'ler olarak çalışır.
- Şu portlar boş olmalı: 3000, 8080, 5433, 6379, 6380, 5701–5703, 9090,
  9404–9406, 3001.

| Adres | İçerik |
|---|---|
| `http://localhost:3000` | **Dashboard (giriş noktası)** |
| `http://localhost:8080/api` | API kökü |
| `http://localhost:8080/api/swagger-ui.html` | Swagger UI |
| `http://localhost:9090` | Prometheus |
| `http://localhost:3001` | Grafana (`.env`'deki `GRAFANA_ADMIN_*` hesabı) |

Giriş için `.env`'deki `UCMD_BOOTSTRAP_USERNAME` / `UCMD_BOOTSTRAP_PASSWORD`
değerlerini kullanın.

**Nasıl çalışır**

- `ucmd-frontend` nginx, `/api/` isteklerini container ağı üzerinden
  `ucmd-backend:8080`'e proxy'ler. Tarayıcı açısından API aynı origin'dedir.
  Buna rağmen tarayıcı POST isteklerinde `Origin` başlığı gönderdiği için
  `http://localhost:3000` backend'in izin listesinde olmak zorundadır
  (`ucmd.cors.allowed-origins`, gerekirse `UCMD_CORS_ALLOWED_ORIGINS` ile
  override edilir). Listede değilse Spring isteği `403 Invalid CORS request`
  ile reddeder ve giriş ekranı "Kullanıcı adı veya şifre hatalı" gösterir.
- Frontend imajı `VITE_API_BASE_URL=/api` ile derlenir. Host'ta `npm run dev`
  bu değişkeni tanımlamaz, `client.js` `http://localhost:8080/api`'ye düşer —
  yani iki çalışma biçimi de bozulmadan yan yana durur.
- `ucmd-backend`, `postgres` ve `redis` servisleri **healthy** olana kadar
  bekler; `ucmd-frontend` de backend healthy olana kadar bekler.
- Varsayılan instance kayıtları (`ucmd.seed.*`) compose'ta servis adlarına
  (`redis`, `redis-2`, `hazelcast`, `hz-analytics`) göre override edilir.
  Host üzerinde çalışırken varsayılanlar `localhost` olarak kalır.

> Instance kayıtları yalnızca `instances` tablosu **boşken** oluşturulur.
> Daha önce `localhost` adresleriyle dolmuş bir veritabanınız varsa adresleri
> UI'daki instance yönetimi ekranından güncelleyin veya volume'ü sıfırlayın.

Durum ve log:

```bash
docker compose ps
```

```bash
docker compose logs -f ucmd-backend
```

---

## 5. Endpoint Listesi

Mimari karar: **okuma uçları POST kullanır** (ADR-0007). İstisnalar `GET /v1/health`,
`GET /v1/audit`, `GET /v1/admin/users`.

### Kimlik doğrulama

| Metot | Yol | Rol | Açıklama |
|---|---|---|---|
| POST | `/v1/auth/login` | — | JWT üretir |
| POST | `/v1/auth/logout` | MONITOR | Token'ı kara listeye alır |

### Sağlık (kimlik doğrulama gerekmez)

| Metot | Yol | Açıklama |
|---|---|---|
| GET | `/v1/health` | UCMD durumu, versiyon, gerçek uptime |
| GET | `/v1/health/app` | Basit canlılık yanıtı |
| GET | `/v1/health/collection` | Toplama kalitesi: OK / PARTIAL / FAILED |

### Instance okuma

| Metot | Yol | Rol | Açıklama |
|---|---|---|---|
| POST | `/v1/instances/list` | MONITOR | Kayıtlı instance'lar |
| POST | `/v1/instances/{id}/detail` | MONITOR | Tek instance kaydı |

### Metrikler (hepsi `@RequireRole("MONITOR")`)

| Metot | Yol | Açıklama |
|---|---|---|
| POST | `/v1/instances/{id}/health` | UP/DOWN, versiyon, uptime |
| POST | `/v1/instances/{id}/resources` | CPU, bellek, fragmentation + JVM/GC/thread/FD |
| POST | `/v1/instances/{id}/connections` | Aktif istemci, bloklu, maxclients |
| POST | `/v1/instances/{id}/data-stats` | Key sayısı, DB dağılımı, TTL, eviction |
| POST | `/v1/instances/{id}/efficiency` | Hit/miss, ops/sec, ağ I/O (HZ: N/A) |
| POST | `/v1/instances/{id}/replication` | Rol, replica'lar, gecikme (HZ: N/A) |
| POST | `/v1/instances/{id}/persistence` | RDB/AOF durumu (HZ: N/A) |
| POST | `/v1/instances/{id}/cluster` | Üyeler, partition dağılımı, skew |
| POST | `/v1/instances/{id}/map-stats` | IMap entry/bellek/operasyon (Redis: N/A) |
| POST | `/v1/instances/{id}/operation-stats` | Operation/event kuyruğu, executor (Redis: N/A) |

### Key analizi (pahalı — tüm korumalar aktif)

| Metot | Yol | Rol | Açıklama |
|---|---|---|---|
| POST | `/v1/instances/{id}/keys` | MONITOR | Redis: SCAN. HZ: `mapName` boşsa map adları, doluysa entry key'leri |
| POST | `/v1/instances/{id}/keys/top` | MONITOR | En büyük N key |
| POST | `/v1/instances/{id}/keys/ttl` | MONITOR | Tek key TTL |
| POST | `/v1/instances/{id}/key-patterns` | MONITOR | Desen bazlı gruplama |

### Zaman serisi

| Metot | Yol | Rol | Açıklama |
|---|---|---|---|
| POST | `/v1/instances/{id}/snapshots` | MONITOR | Ham snapshot listesi |
| POST | `/v1/instances/{id}/history` | MONITOR | Örneklenmiş seri (RAW / DOWNSAMPLED) |
| POST | `/v1/instances/{id}/capacity-forecast` | MONITOR | Son `windowHours` saatin (varsayılan 24) bellek eğilimi: günlük büyüme ve sınıra kalan gün |

Kapasite tahmini saklanan bellek ölçümlerine doğru uyduruyor. En az ~30 dakikalık veri
istiyor (öncesinde `NOT_READY`), sınır yoksa (Redis `maxmemory 0`) `NOT_APPLICABLE`
dönüyor, bellek büyümüyorsa tarih yerine `STABLE`/`DECREASING` diyor ve gürültülü bir
eğilimi ayırt etmek için R² veriyor. Instance sayfasında "Belleğin Dolmasına" kartı olarak
görünüyor; `DAYS_TO_FULL` alarm metriği de bunu kullanıyor ("14 günden az kaldı").

### Alarmlar

| Metot | Yol | Rol | Açıklama |
|---|---|---|---|
| POST | `/v1/alerts/active` | MONITOR | Şu an süren alarmlar |
| POST | `/v1/alerts/history` | MONITOR | Başlayan/düzelen olaylar, yeniden eskiye (`instanceId`, `limit` en fazla 500) |
| POST | `/v1/alerts/rules` | MONITOR | Tanımlı kurallar |

Kurallar `ucmd.alerts.rules` altında (metrik, `GT`/`LT`, eşik, `for-minutes`, önem,
isteğe bağlı instance adları) ve 30 sn'lik her toplama turunda o an alınan
snapshot üzerinden değerlendiriliyor; alarm için izlenen sisteme ek istek gitmiyor.
Metrikler: `INSTANCE_DOWN`, `MEMORY_PERCENT`, `CPU_PERCENT`, `HIT_RATIO_PERCENT`,
`FRAGMENTATION_RATIO`, `CLIENT_COUNT`, `CONNECTED_REPLICAS`, `EVICTIONS_PER_MINUTE`,
`DAYS_TO_FULL`. Hesaplanamayan metrik (maxmemory yok, hit oranı `null`) karar üretmiyor. Alarm
başlarken ve düzelirken bildiriliyor, sürdükçe `repeat-interval-minutes`'ta bir
hatırlatılıyor. Kanallar ortam değişkenleriyle açılıyor:
`UCMD_ALERT_TELEGRAM_BOT_TOKEN` + `UCMD_ALERT_TELEGRAM_CHAT_ID`,
`UCMD_ALERT_SLACK_WEBHOOK_URL`, `UCMD_ALERT_WEBHOOK_URL` (ham JSON olay),
`UCMD_ALERT_EMAIL_TO` (ve `spring.mail.*`). Olaylar `alert_events`'te 90 gün tutuluyor,
denetim kaydına da düşüyor. Frontend'deki **Alarmlar** sayfası üçünü de gösteriyor.
Ayrıntı: [ADR-0015](docs/adr/ADR-0015-esik-alarm-motoru.md).

### Yönetim (yazma yüzeyi)

| Metot | Yol | Rol | Açıklama |
|---|---|---|---|
| POST | `/v1/admin/instances` | ADMIN | Instance kaydı oluştur |
| PUT | `/v1/admin/instances/{id}` | ADMIN | Güncelle (cache + HZ client geçersizlenir) |
| DELETE | `/v1/admin/instances/{id}` | ADMIN | Sil |
| GET | `/v1/admin/users` | ADMIN | Kullanıcı listesi |
| POST | `/v1/admin/users` | ADMIN | Kullanıcı oluştur |
| DELETE | `/v1/admin/users/{username}` | ADMIN | Kullanıcı sil |
| GET | `/v1/audit` | ADMIN | Sayfalı denetim kaydı |

**Swagger:** `http://localhost:8080/api/swagger-ui.html` — `monitoring` grubu salt-okunur
yüzeyi, `admin` grubu yazma uçlarını gösterir. Teslim dokümanı `monitoring` grubudur.

Elle test için: [`docs/api-examples.http`](docs/api-examples.http)

---

## 6. Güvenlik Modeli

### Kimlik doğrulama

JWT (HMAC-SHA256, jjwt 0.12.6). `AuthInterceptor` her istekte token imzasını ve
süresini doğrular, kara liste kontrolü yapar, kullanıcıyı DB'den okuyup
`request.currentUser` olarak koyar. Çıkışta token süresi dolana kadar kara listede kalır.

Muaf yollar: `/v1/auth/login`, `/v1/health/**`, `/actuator/**`, `/swagger-ui/**`,
`/v3/api-docs/**`.

### Rol hiyerarşisi

```
ADMIN  ⊇  MONITOR
```

`@RequireRole("MONITOR")` bir uçta ADMIN de geçer; `@RequireRole("ADMIN")` yalnızca
ADMIN geçer. Ek olarak `AuthInterceptor` savunma derinliği uygular: `/v1/admin/`
içeren her yol, anotasyon unutulsa bile ADMIN olmayan kullanıcıya **403** döner.

Parolalar BCrypt ile özetlenir. İlk açılışta `UCMD_BOOTSTRAP_*` ile oluşturulan yönetici hesabı
silinemez (sisteme erişimi kaybetmemek için); `APP_ENVIRONMENT=production` ise bu hesap hiç oluşturulmaz.

### Key maskeleme (ADR-0010)

Fintech ortamında key adı hassas veri taşır (`ucmd:merchant:88123:commission` komisyon
oranını, `...:risk` risk skorunu açığa çıkarır).

- **Kural:** 3+ haneli rakam dizileri → `***`
- **Varsayılan:** açık (`ucmd.security.key-masking.enabled`)
- **Rol istisnası yok** — ADMIN de maskeli görür (denetim izini bulanıklaştırmamak için)
- `mask()` yapılandırmaya uyar; `maskForLog()` **her zaman** maskeler (log ve denetim kaydı)
- İstisna: `KeyTtlResponse.key` maskelenmez — kullanıcı ne sorguladığını bilmeli.
  Denetim kaydına maskeli yazılır.

### Allowlist

| Ayar | Etki |
|---|---|
| `ucmd.security.allowed-prefixes` | Boşsa kısıt yok. Doluysa Redis prefix'i listede olmalı → aksi halde **400 · `invalid-prefix`** |
| `ucmd.security.allowed-maps` | Boşsa kısıt yok. Doluysa Hazelcast map adı listede olmalı → aksi halde **403 · `map-not-allowed`** (yetki kısıtı, girdi hatası değil) |

Prod profilinde prefix listesi `ucmd:merchant`, `ucmd:terminal`, `ucmd:transaction`,
`ucmd:stats` ile sınırlıdır.

### Salt-okunur ilkesi (ADR-0001)

Redis'te yalnızca `PING`, `INFO`, `SCAN`, `TYPE`, `TTL`, `PTTL`, `MEMORY USAGE`.
Hazelcast'te yalnızca `size()`, `getDistributedObjects()`, `getCluster()`,
`getPartitionService()`, `getEntryView()`. `KEYS *` yasaktır.

Sunucu tarafı ikinci savunma hattı için [`docs/redis-acl-setup.md`](docs/redis-acl-setup.md).
ACL kullanıcısı bir komuta yetkisizse UCMD `NOPERM` yanıtını ayırt edip
`PERMISSION_DENIED` statüsü döner.

### Prod sıkılaştırması

`ProdSecurityValidation` `prod` profilinde açılışta durur: Redis TLS kapalıysa,
Hazelcast TLS kapalıysa veya Redis parolası boşsa `IllegalStateException`.
Swagger prod'da kapalıdır.

---

## 7. Koruma Katmanı

Tüm monitor çağrıları `ProtectedMonitorExecutor` üzerinden geçer (ADR-0006).

Sıra: **cache → rate limiter → bulkhead → circuit breaker → timeout → çağrı**.
Değerler `src/main/resources/application.yml` içindeki `ucmd.protection` ve `ucmd.cache` ayarlarıdır; kod içi varsayılanlar farklı olabilir.
Cache en dışta: hit durumunda hiçbir limit tüketilmez.

| Ayar | Değer | Uygulandığı yer |
|---|---|---|
| `cheap-concurrency` | 12 | Metrik uçları, instance başına eş zamanlılık |
| `expensive-concurrency` | 2 | SCAN tabanlı uçlar |
| `cheap-timeout-ms` | 5000 | Metrik çağrısı zaman aşımı |
| `expensive-timeout-ms` | 15000 | SCAN çağrısı zaman aşımı |
| `expensive-rate-limit-per-minute` | 10 | Instance başına dakikalık pahalı sorgu |
| `circuit-failure-rate-percent` | 50 | Devreyi açan hata oranı |
| `circuit-sliding-window` | 10 | Oranın ölçüldüğü çağrı sayısı |
| `circuit-open-duration-seconds` | 30 | Devre açık kalma süresi |
| `bulkhead-max-wait-ms` | 2000 | `application.yml` değeri. Yürütücü beklemeyen `tryAcquirePermission` kullandığı için bulkhead dolunca çağrı pratikte beklemeden reddedilir |
| `cache.ttl-seconds` | 10 | Taze kabul süresi |
| `cache.stale-ttl-minutes` | 30 | Son bilinen değerin saklandığı süre |
| `key-search.hazelcast-max-scan-entries` | 5000 | Map içi entry taramasında üst sınır |

**İki yürütme modu:**

- `executeCachedMetric` — ucuz, cache'li. Rate limit/bulkhead reddi dashboard'ı
  kilitlemesin diye uygulanmaz. Hata durumunda son bilinen değer `STALE` olarak döner;
  yoksa `UNAVAILABLE`, zaman aşımında `TIMEOUT`.
- `executeExpensive` — SCAN tabanlı. Tüm limitler aktif, cache yok. Reddedilirse
  **429** (rate limit), **503** (bulkhead / circuit), **504** (timeout).

Reddetme sayaçları `/actuator/prometheus` altında `ucmd_protection_*` olarak yayınlanır.

---

## 8. Özellik Kapsamı

Aşağıdaki tablo, izleme kapsamının hangi kısmının uygulandığını gösterir.

| Konu | Durum | Not |
|---|---|---|
| Sağlık, versiyon, uptime | ✅ | Redis `INFO server`; HZ `runtime.uptime` (Prometheus) |
| CPU ve bellek | ✅ | Redis: delta tabanlı CPU (ilk turda `NOT_READY`); HZ: `processcpuload` |
| Bağlantılar | ✅ | Redis: connected/blocked/maxclients/rejected. HZ: `tcp.connection.clientcount`; gerçek client sayısı, member sayısıyla karıştırılmıyor |
| Key sayısı, TTL, eviction | ✅ | `INFO keyspace` tüm DB'ler toplanır, `avg_ttl` ağırlıklı |
| Hit/miss, throughput, ağ | ✅ Redis / ⚠️ HZ | Hazelcast'te `/efficiency` `NOT_APPLICABLE` döner; map bazlı `hitRatio` `map-stats` içinde, yalnızca sayaçlar tutarlıysa dolu, değilse `null` ([sınırlar](docs/known-limitations.md) madde 11) |
| Replikasyon | ✅ | Master/replica, offset, lag. HZ'de `NOT_APPLICABLE` |
| Kalıcılık | ✅ | RDB/AOF durumu, son bgsave. HZ Hot Restart enterprise → N/A |
| Redis Cluster | ❌ | `cluster_enabled` okunuyor, `CLUSTER INFO/NODES` parse edilmiyor → `NOT_READY` (madde 3) |
| Hazelcast map istatistikleri | ✅ | 16 alan Prometheus scrape'ten: entry, bellek maliyeti, get/put/remove, gecikme |
| Partition dağılımı | ✅ | Gerçek partition sayısı, member başına dağılım, skew %, `clusterSafe` |
| Operation/event | ✅ | Queue, invocation, event, 21 internal executor |
| JVM metrikleri | ✅ | Heap/non-heap, GC, thread, FD, loaded class, system CPU |
| Hazelcast key arama | ⚠️ | Entry araması var ama `keySet()` + `maxScan` tabanlı → `ESTIMATED`. `PagingPredicate` kapsam dışı (madde 2) |
| Öz-izleme | ✅ | 11 Micrometer metriği + `/v1/health/collection` |
| Salt-okunur API yüzeyi | ✅ | Yazma uçları `/v1/admin/**` altında, Swagger `monitoring` grubunda görünmez ([ADR-0011](docs/adr/ADR-0011-yazma-uclarinin-admin-altina-ayrilmasi.md)) |
| Maskeleme ve erişim kontrolü | ✅ | Key/map maskeleme, prefix + map allowlist, ACL dokümanı, `PERMISSION_DENIED` ([ADR-0010](docs/adr/ADR-0010-key-maskeleme-ve-allowlist.md)) |
| Kapasite tahmini | ✅ | Snapshot geçmişinden doğrusal bellek eğilimi, sınıra kalan gün; alarm kuralı olarak da kullanılabiliyor |
| Eşik alarmları | ✅ | Toplanan snapshot üzerinde süreli ihlal kuralları, Telegram/Slack/webhook/e-posta, geçmiş `alert_events`'te ([ADR-0015](docs/adr/ADR-0015-esik-alarm-motoru.md)) |
| Denetim kaydı | ✅ | DB'de, 90 gün, kimlik + mutasyon + key erişimi (metrik uçları hariç) ([ADR-0012](docs/adr/ADR-0012-audit-log-db-ve-kapsam.md)) |
| Vault | ❌ | Kapsam dışı. Credential env değişkeninden okunur, parola DB'de saklanmaz (madde 6) |
| Export / download | ❌ | Bilinçli olarak kapsam dışı (madde 9) |
| Value görüntüleme | ❌ | Bilinçli olarak kapsam dışı; salt-okunur ilkesiyle de çelişir (madde 9) |
| Yanlış değer üretmeme | ✅ | Durum modeli uygulanıyor ([ADR-0003](docs/adr/ADR-0003-metricstatus-durum-modeli.md)): bilinmeyen değer `UNAVAILABLE` döner. Sınırlar dokümanındaki 11–13. maddeler bu ilkenin ihlalleriydi ve giderildi |

Ayrıntılı gerekçeler için [`docs/adr/`](docs/adr/), "madde N" gönderimleri için
[`docs/known-limitations.md`](docs/known-limitations.md).

---

## 9. Bilinen Sınırlar

Tam liste: [`docs/known-limitations.md`](docs/known-limitations.md)

Öne çıkanlar:
- Hazelcast entry araması `keySet()` tabanlı, örneklem sınırlı (`ESTIMATED`)
- HZ map bazlı `hitRatio`, sayaçlar node'lar arasında tutarsız olduğunda `null` döner (eskiden 1'i aşabiliyordu)
- Redis Cluster (`CLUSTER INFO/NODES`) desteklenmiyor
- Vault entegrasyonu yok
- TLS desteği kodda mevcut ancak TLS'li bir ortamda doğrulanmadı

---

## 10. Geliştirme Notları

### Şema yönetimi

H2 test profili dışında her profilde şemayı Flyway yönetiyor; test profili şemayı
entity'lerden kuruyor. Şema değişikliği `db/migration` altına yeni bir `V<n>__aciklama.sql`
dosyasıdır; uygulanmış dosyalar değiştirilmez (Flyway sağlama toplamlarını denetliyor).

### Test

```bash
mvn clean test
```

GitHub Actions, `main`/`claude`'a her push'ta ve pull request'lerde `mvn verify`'ı ve frontend lint + build'i çalıştırıyor.

109 test, iki katman:

| Katman | Kapsam |
|---|---|
| Unit | INFO parser, keyspace parser, CPU delta, hit ratio, cursor pagination, metric status eşleme, HZ partition aggregation, sampling, maskeleme, Prometheus parser |
| Integration | Redis standalone, Redis ACL + yetki reddi, bağlantı kaybı, HZ multi-member, koruma katmanı (timeout / STALE / bulkhead / rate limit) |

**Docker gereksinimi.** Redis integration testleri Testcontainers ile gerçek bir
`redis:7-alpine` container'ı ayağa kaldırır. Docker yoksa bu sınıflar
`@Testcontainers(disabledWithoutDocker = true)` sayesinde **atlanır**, build kırılmaz.
Hazelcast testleri gömülü member kullanır, Docker gerektirmez.

**IntelliJ'den çalıştırma.** `pom.xml`'deki surefire `environmentVariables` bloğu
yalnızca Maven üzerinden geçerlidir. IntelliJ'den test çalıştıracaksan
Run/Debug Configurations → Templates → JUnit → Environment variables kısmına
şunları ekle, yoksa `contextLoads` ve ACL testi kırmızı olur:

```
JWT_SECRET=ucmd-test-secret-key-min-32-characters-long
UCMD_BOOTSTRAP_USERNAME=test-admin
UCMD_BOOTSTRAP_PASSWORD=test-admin-pass
UCMD_TEST_REDIS_PASSWORD=ucmdTestPass
UCMD_TEST_REDIS_LIMITED_PASSWORD=limitedTestPass
```

Test profili H2 in-memory kullanır (`src/test/resources/application-test.yml`);
PostgreSQL'in ayakta olması gerekmez.

`RedisMonitor` ve `HazelcastMonitor`'daki ayrıştırıcılar `private`. Testler bunlara
`ReflectionTestUtils.invokeMethod` ile erişir — üretim kodunda test uğruna
görünürlük gevşetilmemiştir.

Şu an repoda CI iş akışı yok.

### Profiller
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

Prod profilinde TLS zorunludur ve Swagger kapalıdır. Profil verilmezse `dev` çalışır.

### `open-in-view`

`false` — lazy loading'in HTTP katmanına sızmasını engeller. Repository çağrıları
servis sınırında tamamlanır.

### Prometheus scrape bağımlılığı

Hazelcast'in map, operation, event, JVM ve partition metriklerinin **tamamı**
jmx_exporter scrape'inden gelir; Hazelcast Java client bunları sunmaz. Bir instance'ın
`metrics_url` kolonu boşsa ilgili metrikler `UNAVAILABLE` döner — sahte sıfır üretilmez.

Parser `com.ucmd.monitor.prometheus` paketindedir ve etiket duyarlıdır:
`prefix` etiketiyle alan ayrımı yapar (`queuesize` hem `operation` hem
`executor.internal` altında vardır), `tag0` içindeki escape'li nesne adını çözer.

### Log seviyeleri

`dev`: `com.ucmd` DEBUG. `prod`: root WARN, `com.ucmd` INFO.
Key adı, prefix veya pattern içeren log satırı bırakılmamalıdır — maskeleme kuralı
loglar için de geçerlidir.

---

## Lisans

MIT — ayrıntılar için [`LICENSE`](LICENSE).
