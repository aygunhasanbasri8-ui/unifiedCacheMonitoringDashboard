# UCMD — Unified Cache Monitoring Dashboard

English | [Türkçe](README.tr.md)

[![CI](https://github.com/aygunhasanbasri8-ui/unifiedCacheMonitoringDashboard/actions/workflows/ci.yml/badge.svg)](https://github.com/aygunhasanbasri8-ui/unifiedCacheMonitoringDashboard/actions/workflows/ci.yml)

## 1. Overview

UCMD is a **read-only** monitoring tool that watches Redis and Hazelcast instances in a
distributed environment from a single place. It brings cache systems that different teams
inspect with different tools (redis-cli, Hazelcast Management Center, Grafana) under one API
and one dashboard.

The problem it solves: during a production incident, answering "is the cache full, which
keys are growing, is a replica lagging, which node is under load" today means looking at
several separate tools. UCMD presents that information in one place with a consistent status model.

The key design principle is **never harm the monitored system**. No write command is
ever executed, `KEYS *` is never used, every call goes through a rate limit / bulkhead /
circuit breaker / timeout chain, and a metric that cannot be reached returns `UNAVAILABLE`
instead of a made-up value.

---

## 2. Architecture

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
│ CacheMonitor (interface)│  ← MonitorRegistry picks by type
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

Side branches:
  • PostgreSQL  → instances, metric_snapshots, audit_logs, users
  • Caffeine    → 10 s TTL metric cache + 30 min STALE fallback window
  • Micrometer  → /actuator/prometheus (UCMD's own self-monitoring metrics)
  • SnapshotScheduler → collects a snapshot from every instance every 30 s
```

**Connection pools:** `RedisConnectionManager` (keyed by `host:port:username`),
`HazelcastConnectionManager` (keyed by `clusterName|members`). Both cache connections,
re-establish dead ones and close them with `@PreDestroy`.

---

## 3. Tech Stack

| Component | Version | Note |
|---|---|---|
| Java | 21 | Virtual Threads enabled (`spring.threads.virtual.enabled`) |
| Spring Boot | 3.5.16 | Web, Data JPA, Actuator, AOP |
| PostgreSQL | 15 | `ucmd_db` |
| H2 | test | in-memory DB for the test profile |
| Testcontainers | managed by Spring Boot | real Redis in integration tests |
| Lettuce | managed by Spring Boot | `RedisClient`, `ByteArrayCodec` |
| Hazelcast Client | 5.3.8 | unisocket (`smartRouting=false`) |
| resilience4j | 2.2.0 | rate limiter, bulkhead, circuit breaker |
| Caffeine | managed by Spring Boot | short-TTL metric cache |
| Micrometer + Prometheus | managed by Spring Boot | `/actuator/prometheus` |
| springdoc-openapi | 2.8.6 | Swagger UI, two groups |
| jjwt | 0.12.6 | HMAC-SHA256 |
| BCrypt | spring-security-crypto | password hashing |
| Frontend | React 19, Vite, React Router, Recharts, Axios | under `frontend/`; served by nginx in Docker |

> **Note:** springdoc 2.6.0 is binary-incompatible with Spring Framework 6.2
> (`NoSuchMethodError: ControllerAdviceBean.<init>`). Use 2.8.6.

---

## 4. Setup

### 4.1 Prerequisites

- JDK 21
- Maven 3.9+ (or the `mvnw` in the repo)
- Docker + Docker Compose
- PostgreSQL 15 (comes with compose)

> If you run everything from Docker (section 4.6), **only Docker** is required; the JDK,
> Maven, Node and PostgreSQL live inside the images.

### 4.2 `.env` variables

Create a `.env` file in the repo root:

| Variable | Required | Description |
|---|---|---|
| `JWT_SECRET` | ✅ | At least 32 characters. If shorter, the app stops at startup |
| `JWT_EXPIRATION_HOURS` | — | Default 24 |
| `UCMD_BOOTSTRAP_USERNAME` | ✅ (dev) | Name of the single admin account created on first start |
| `UCMD_BOOTSTRAP_PASSWORD` | ✅ (dev) | Password of that account |
| `UCMD_BOOTSTRAP_ROLE` | — | Default `ADMIN` |
| `SPRING_DATASOURCE_URL` | — | Default `jdbc:postgresql://localhost:5432/ucmd_db` |
| `SPRING_DATASOURCE_USERNAME` | ✅ | No default. In Docker Compose it comes from `POSTGRES_USER` |
| `SPRING_DATASOURCE_PASSWORD` | ✅ | No default. In Docker Compose it comes from `POSTGRES_PASSWORD` |
| `POSTGRES_USER`, `POSTGRES_PASSWORD` | ✅ (Compose) | Shared credentials of the `postgres` and `ucmd-backend` services |
| `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD` | ✅ (Compose) | Grafana admin account |
| `REDIS_CACHE_PASSWORD` | ✅ (Compose) | Password of `redis-2` (6380) |
| `UCMD_CORS_ALLOWED_ORIGINS` | — | Default `http://localhost:5173,http://localhost:5174,http://localhost:3000` |
| `APP_ENVIRONMENT` | — | If `production`, the bootstrap admin account is not created |
| `<INSTANCE_PASSWORD_ENV>` | — | Redis ACL password; its name is stored in the `instances.password_env_var` column |

An example file with every Compose variable: [`.env.example`](.env.example).

> Passwords are **not stored in the database**. `InstanceRef.passwordEnvVar` only carries
> the **name** of the environment variable to read from. For the server-side second line of
> defense see [`docs/redis-acl-setup.md`](docs/redis-acl-setup.md); the limits of this
> approach are in [`docs/known-limitations.md`](docs/known-limitations.md) item 6.

> There is **no** hard-coded default username or password in the code. On first start the
> app only creates the single admin account defined by `UCMD_BOOTSTRAP_*`; other users are
> created with that account from the user management screen in the UI. If the variables are
> missing, the app stops at startup.
>
> If the account already exists in the database these values are ignored. To change the
> password, delete the account and restart the app.

### 4.3 Start the infrastructure

The compose file requires the `.env` variables; first create it with `cp .env.example .env`
and fill in the values.

```bash
docker compose up -d
```

Services that come up:

| Service | Port | Description |
|---|---|---|
| `redis` | 6379 | 256 MB, `allkeys-lru` |
| `redis-2` | 6380 | 128 MB, `allkeys-lru` |
| `hazelcast` + `hazelcast-2` | 5701, 5702 | `dev` cluster, 2 nodes |
| `hz-analytics` | 5703 | `analytics` cluster, 1 node |
| jmx_exporter | 9404, 9405, 9406 | Prometheus scrape of the Hazelcast nodes |
| `postgres` | 5433 | `ucmd_db` (5432 inside the container) |
| `prometheus` | 9090 | — |
| `grafana` | 3001 | Account from `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` in `.env` |
| `ucmd-backend` | 8080 | Spring Boot API (optional, see section 4.6) |
| `ucmd-frontend` | 3000 | React + nginx (optional, see section 4.6) |

> `docker compose up -d` starts **all** services. If you only want the infrastructure,
> name the services explicitly:
>
> ```bash
> docker compose up -d redis redis-2 hazelcast hazelcast-2 hz-analytics postgres prometheus grafana
> ```

Verify:

```bash
docker logs hazelcast 2>&1 | grep -A3 "Members {"
```

```bash
docker logs hz-analytics 2>&1 | grep -A2 "Members {"
```

```bash
curl -s localhost:9406/metrics | grep jvm_memory_bytes_used | head -3
```

You should see `size:2` for `hazelcast` and `size:1` for `hz-analytics`.

### 4.4 Database

`ddl-auto: update` creates the tables in the dev profile but is **not reliable** (see
[`docs/known-limitations.md`](docs/known-limitations.md) item 5). Column additions must be done by hand.
The prod profile uses `validate`.

If you are setting up the schema from scratch, the file in the "Database schema" section
below is enough. The SQL here is for upgrading an **older** database that was created
before these columns existed:

```sql
-- ── Hazelcast multi-cluster ──
ALTER TABLE instances
  ADD COLUMN IF NOT EXISTS cluster_name VARCHAR(255),
  ADD COLUMN IF NOT EXISTS members      VARCHAR(1000),
  ADD COLUMN IF NOT EXISTS metrics_url  VARCHAR(1000);

-- ── Audit log ──
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

-- ── Redis ACL credential support ──
ALTER TABLE instances
  ADD COLUMN IF NOT EXISTS username          VARCHAR(255),
  ADD COLUMN IF NOT EXISTS password_env_var  VARCHAR(255);
```

The default instance records (`Redis-prod`, `Redis-cache`, `Hazelcast-dev`,
`Hazelcast-analytics`) are created by the app from the `ucmd.seed.*` settings when the
`instances` table is empty; no manual `INSERT` is needed.

### Database schema

`ddl-auto: update` did not add columns reliably in this project. The schema source is
`docs/migrations/001-initial-schema.sql`.

First setup on a clean environment:

```bash
psql -U ucmd_user -d ucmd_db -f docs/migrations/001-initial-schema.sql
```

This file only creates UCMD's own tables (`instances`, `users`, `metric_snapshots`,
`audit_logs`). Tables produced by the simulation scripts under `sim/` are not included.

### 4.5 Run the application

```bash
mvn spring-boot:run
```

The app does not read the `.env` file itself; the variables (especially `JWT_SECRET`,
`UCMD_BOOTSTRAP_*`, `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`) must be
defined in the environment. On Windows, the `run.ps1` in the repo can load `.env` and start the app.

- API root: `http://localhost:8080/api`
- Swagger UI: `http://localhost:8080/api/swagger-ui.html`

To run the frontend separately:

```bash
cd frontend && npm install && npm run dev
```

Vite opens on `http://localhost:5173` and calls the API directly at
`http://localhost:8080/api` (the CORS allowance is defined in `CorsConfig`).

### 4.6 Run everything with Docker

The backend and frontend are part of the compose file too; the whole system comes up
without installing a JDK or Node on the host. **The only prerequisite is Docker.**

On a fresh machine:

```bash
git clone https://github.com/aygunhasanbasri8-ui/unifiedCacheMonitoringDashboard.git
```

```bash
cd unifiedCacheMonitoringDashboard && cp .env.example .env
```

`.env` is not kept in git and has to be created on every machine. Fill in `JWT_SECRET`
(at least 32 characters) and the admin account's username and password, then:

```bash
docker compose up -d --build
```

The first build takes 5-10 minutes because it downloads the Maven dependencies; later
builds finish in seconds from the layer cache.

**Machine requirements**

- Docker should get at least ~4 GB of RAM: the backend and the 3 Hazelcast nodes run as
  separate JVMs.
- These ports must be free: 3000, 8080, 5433, 6379, 6380, 5701-5703, 9090, 9404-9406, 3001.

| URL | Content |
|---|---|
| `http://localhost:3000` | **Dashboard (entry point)** |
| `http://localhost:8080/api` | API root |
| `http://localhost:8080/api/swagger-ui.html` | Swagger UI |
| `http://localhost:9090` | Prometheus |
| `http://localhost:3001` | Grafana (the `GRAFANA_ADMIN_*` account from `.env`) |

Log in with `UCMD_BOOTSTRAP_USERNAME` / `UCMD_BOOTSTRAP_PASSWORD` from `.env`.

**How it works**

- The `ucmd-frontend` nginx proxies `/api/` requests to `ucmd-backend:8080` over the
  container network. From the browser's point of view the API is same-origin. Even so,
  browsers send an `Origin` header on POST requests, so `http://localhost:3000` has to be in
  the backend's allow list (`ucmd.cors.allowed-origins`, overridable with
  `UCMD_CORS_ALLOWED_ORIGINS`). If it is not, Spring rejects the request with
  `403 Invalid CORS request` and the login screen shows a "wrong username or password" message.
- The frontend image is built with `VITE_API_BASE_URL=/api`. `npm run dev` on the host does
  not define this variable and `client.js` falls back to `http://localhost:8080/api`, so both
  ways of running sit side by side without breaking each other.
- `ucmd-backend` waits until the `postgres` and `redis` services are **healthy**;
  `ucmd-frontend` waits until the backend is healthy.
- The default instance records (`ucmd.seed.*`) are overridden in compose to use the service
  names (`redis`, `redis-2`, `hazelcast`, `hz-analytics`). When running on the host the
  defaults stay `localhost`.

> Instance records are only created while the `instances` table is **empty**. If you have a
> database already filled with `localhost` addresses, update the addresses from the instance
> management screen in the UI or reset the volume.

Status and logs:

```bash
docker compose ps
```

```bash
docker compose logs -f ucmd-backend
```

---

## 5. Endpoints

Architectural decision: **read endpoints use POST** (ADR-0007). The exceptions are
`GET /v1/health`, `GET /v1/audit` and `GET /v1/admin/users`.

### Authentication

| Method | Path | Role | Description |
|---|---|---|---|
| POST | `/v1/auth/login` | — | Issues a JWT |
| POST | `/v1/auth/logout` | MONITOR | Blacklists the token |

### Health (no authentication required)

| Method | Path | Description |
|---|---|---|
| GET | `/v1/health` | UCMD status, version, real uptime |
| GET | `/v1/health/app` | Simple liveness response |
| GET | `/v1/health/collection` | Collection quality: OK / PARTIAL / FAILED |

### Instance reads

| Method | Path | Role | Description |
|---|---|---|---|
| POST | `/v1/instances/list` | MONITOR | Registered instances |
| POST | `/v1/instances/{id}/detail` | MONITOR | A single instance record |

### Metrics (all `@RequireRole("MONITOR")`)

| Method | Path | Description |
|---|---|---|
| POST | `/v1/instances/{id}/health` | UP/DOWN, version, uptime |
| POST | `/v1/instances/{id}/resources` | CPU, memory, fragmentation + JVM/GC/thread/FD |
| POST | `/v1/instances/{id}/connections` | Active clients, blocked, maxclients |
| POST | `/v1/instances/{id}/data-stats` | Key count, DB distribution, TTL, eviction |
| POST | `/v1/instances/{id}/efficiency` | Hit/miss, ops/sec, network I/O (HZ: N/A) |
| POST | `/v1/instances/{id}/replication` | Role, replicas, lag (HZ: N/A) |
| POST | `/v1/instances/{id}/persistence` | RDB/AOF state (HZ: N/A) |
| POST | `/v1/instances/{id}/cluster` | Members, partition distribution, skew |
| POST | `/v1/instances/{id}/map-stats` | IMap entries/memory/operations (Redis: N/A) |
| POST | `/v1/instances/{id}/operation-stats` | Operation/event queues, executors (Redis: N/A) |

### Key analysis (expensive, all protections active)

| Method | Path | Role | Description |
|---|---|---|---|
| POST | `/v1/instances/{id}/keys` | MONITOR | Redis: SCAN. HZ: map names if `mapName` is empty, entry keys otherwise |
| POST | `/v1/instances/{id}/keys/top` | MONITOR | The N largest keys |
| POST | `/v1/instances/{id}/keys/ttl` | MONITOR | TTL of a single key |
| POST | `/v1/instances/{id}/key-patterns` | MONITOR | Grouping by pattern |

### Time series

| Method | Path | Role | Description |
|---|---|---|---|
| POST | `/v1/instances/{id}/snapshots` | MONITOR | Raw snapshot list |
| POST | `/v1/instances/{id}/history` | MONITOR | Sampled series (RAW / DOWNSAMPLED) |

### Administration (write surface)

| Method | Path | Role | Description |
|---|---|---|---|
| POST | `/v1/admin/instances` | ADMIN | Create an instance record |
| PUT | `/v1/admin/instances/{id}` | ADMIN | Update (invalidates cache + HZ client) |
| DELETE | `/v1/admin/instances/{id}` | ADMIN | Delete |
| GET | `/v1/admin/users` | ADMIN | User list |
| POST | `/v1/admin/users` | ADMIN | Create a user |
| DELETE | `/v1/admin/users/{username}` | ADMIN | Delete a user |
| GET | `/v1/audit` | ADMIN | Paged audit log |

**Swagger:** `http://localhost:8080/api/swagger-ui.html`. The `monitoring` group shows the
read-only surface, the `admin` group shows the write endpoints. The deliverable
documentation is the `monitoring` group.

For manual testing: [`docs/api-examples.http`](docs/api-examples.http)

---

## 6. Security Model

### Authentication

JWT (HMAC-SHA256, jjwt 0.12.6). On every request `AuthInterceptor` verifies the token's
signature and expiry, checks the blacklist, reads the user from the DB and puts it in
`request.currentUser`. On logout the token stays blacklisted until it expires.

Exempt paths: `/v1/auth/login`, `/v1/health/**`, `/actuator/**`, `/swagger-ui/**`,
`/v3/api-docs/**`.

### Role hierarchy

```
ADMIN  ⊇  MONITOR
```

On an endpoint with `@RequireRole("MONITOR")` ADMIN passes as well; `@RequireRole("ADMIN")`
lets only ADMIN through. In addition, `AuthInterceptor` applies defense in depth: any path
containing `/v1/admin/` returns **403** to a non-ADMIN user even if the annotation was forgotten.

Passwords are hashed with BCrypt. The admin account created on first start from
`UCMD_BOOTSTRAP_*` cannot be deleted (so access is never lost); with
`APP_ENVIRONMENT=production` this account is not created at all.

### Key masking (ADR-0010)

In a fintech environment a key name can carry sensitive data (`ucmd:merchant:88123:commission`
exposes a commission rate, `...:risk` a risk score).

- **Rule:** digit sequences of 3 or more digits become `***`
- **Default:** on (`ucmd.security.key-masking.enabled`)
- **No role exception:** ADMIN sees masked keys too (so the audit trail is not blurred)
- `mask()` follows the configuration; `maskForLog()` **always** masks (logs and audit log)
- Exception: `KeyTtlResponse.key` is not masked, because the user must know what they
  queried. It is written masked to the audit log.

### Allowlist

| Setting | Effect |
|---|---|
| `ucmd.security.allowed-prefixes` | No restriction if empty. If set, the Redis prefix must be in the list, otherwise **400 · `invalid-prefix`** |
| `ucmd.security.allowed-maps` | No restriction if empty. If set, the Hazelcast map name must be in the list, otherwise **403 · `map-not-allowed`** (an authorization limit, not an input error) |

In the prod profile the prefix list is limited to `ucmd:merchant`, `ucmd:terminal`,
`ucmd:transaction`, `ucmd:stats`.

### Read-only principle (ADR-0001)

On Redis only `PING`, `INFO`, `SCAN`, `TYPE`, `TTL`, `PTTL`, `MEMORY USAGE`.
On Hazelcast only `size()`, `getDistributedObjects()`, `getCluster()`,
`getPartitionService()`, `getEntryView()`. `KEYS *` is forbidden.

For the server-side second line of defense see [`docs/redis-acl-setup.md`](docs/redis-acl-setup.md).
If an ACL user is not permitted to run a command, UCMD recognizes the `NOPERM` reply and
returns the `PERMISSION_DENIED` status.

### Prod hardening

`ProdSecurityValidation` stops the app at startup in the `prod` profile with an
`IllegalStateException` if Redis TLS is off, Hazelcast TLS is off or the Redis password is
empty. Swagger is disabled in prod.

---

## 7. Protection Layer

All monitor calls go through `ProtectedMonitorExecutor` (ADR-0006).

Order: **cache → rate limiter → bulkhead → circuit breaker → timeout → call**.
The cache is outermost: on a hit no limit is consumed.
The values are the `ucmd.protection` and `ucmd.cache` settings in
`src/main/resources/application.yml`; the defaults inside the code may differ.

| Setting | Value | Applies to |
|---|---|---|
| `cheap-concurrency` | 12 | Metric endpoints, concurrency per instance |
| `expensive-concurrency` | 2 | SCAN-based endpoints |
| `cheap-timeout-ms` | 5000 | Metric call timeout |
| `expensive-timeout-ms` | 15000 | SCAN call timeout |
| `expensive-rate-limit-per-minute` | 10 | Expensive queries per minute per instance |
| `circuit-failure-rate-percent` | 50 | Failure rate that opens the circuit |
| `circuit-sliding-window` | 10 | Number of calls the rate is measured over |
| `circuit-open-duration-seconds` | 30 | How long the circuit stays open |
| `bulkhead-max-wait-ms` | 2000 | Value in `application.yml`. The executor uses the non-blocking `tryAcquirePermission`, so when the bulkhead is full the call is in practice rejected without waiting |
| `cache.ttl-seconds` | 10 | How long a value counts as fresh |
| `cache.stale-ttl-minutes` | 30 | How long the last known value is kept |
| `key-search.hazelcast-max-scan-entries` | 5000 | Upper bound for scanning entries inside a map |

**Two execution modes:**

- `executeCachedMetric`: cheap, cached. Rate limit/bulkhead rejection is not applied so a
  rejection never locks the dashboard. On failure the last known value is returned as `STALE`;
  otherwise `UNAVAILABLE`, or `TIMEOUT` on a timeout.
- `executeExpensive`: SCAN-based. All limits are active, no cache. If rejected it returns
  **429** (rate limit), **503** (bulkhead / circuit) or **504** (timeout).

Rejection counters are published under `/actuator/prometheus` as `ucmd_protection_*`.

---

## 8. Feature Coverage

The table shows which parts of the monitoring scope are implemented.

| Topic | Status | Note |
|---|---|---|
| Health, version, uptime | ✅ | Redis `INFO server`; HZ `runtime.uptime` (Prometheus) |
| CPU and memory | ✅ | Redis: delta-based CPU (`NOT_READY` on the first round); HZ: `processcpuload` |
| Connections | ✅ | Redis: connected/blocked/maxclients/rejected. HZ: `tcp.connection.clientcount`; the real client count, not confused with the member count |
| Key count, TTL, eviction | ✅ | `INFO keyspace` summed over all DBs, `avg_ttl` weighted |
| Hit/miss, throughput, network | ✅ Redis / ⚠️ HZ | On Hazelcast `/efficiency` returns `NOT_APPLICABLE`; the per-map `hitRatio` is inside `map-stats`, filled only when the counters are consistent, otherwise `null` ([limitations](docs/known-limitations.md) item 11) |
| Replication | ✅ | Master/replica, offset, lag. `NOT_APPLICABLE` on HZ |
| Persistence | ✅ | RDB/AOF state, last bgsave. HZ Hot Restart is enterprise → N/A |
| Redis Cluster | ❌ | `cluster_enabled` is read, `CLUSTER INFO/NODES` is not parsed → `NOT_READY` (item 3) |
| Hazelcast map statistics | ✅ | 16 fields from the Prometheus scrape: entries, memory cost, get/put/remove, latency |
| Partition distribution | ✅ | Real partition count, distribution per member, skew %, `clusterSafe` |
| Operation/event | ✅ | Queues, invocations, events, 21 internal executors |
| JVM metrics | ✅ | Heap/non-heap, GC, threads, FD, loaded classes, system CPU |
| Hazelcast key search | ⚠️ | Entry search exists but is `keySet()` + `maxScan` based → `ESTIMATED`. `PagingPredicate` is out of scope (item 2) |
| Self-monitoring | ✅ | 11 Micrometer metrics + `/v1/health/collection` |
| Read-only API surface | ✅ | Write endpoints are under `/v1/admin/**` and do not appear in the Swagger `monitoring` group ([ADR-0011](docs/adr/ADR-0011-yazma-uclarinin-admin-altina-ayrilmasi.md)) |
| Masking and access control | ✅ | Key/map masking, prefix + map allowlist, ACL document, `PERMISSION_DENIED` ([ADR-0010](docs/adr/ADR-0010-key-maskeleme-ve-allowlist.md)) |
| Audit log | ✅ | In the DB, 90 days, identity + mutations + key access (metric endpoints excluded) ([ADR-0012](docs/adr/ADR-0012-audit-log-db-ve-kapsam.md)) |
| Vault | ❌ | Out of scope. Credentials are read from an environment variable, passwords are not stored in the DB (item 6) |
| Export / download | ❌ | Deliberately out of scope (item 9) |
| Viewing values | ❌ | Deliberately out of scope; it would also contradict the read-only principle (item 9) |
| Never producing wrong values | ✅ | The status model is applied ([ADR-0003](docs/adr/ADR-0003-metricstatus-durum-modeli.md)): an unknown value returns `UNAVAILABLE`. Items 11-13 in the limitations document violated this principle and have been fixed |

For detailed rationale see [`docs/adr/`](docs/adr/) (written in Turkish); the "item N"
references point to [`docs/known-limitations.md`](docs/known-limitations.md).

---

## 9. Known Limitations

Full list: [`docs/known-limitations.md`](docs/known-limitations.md) (in Turkish)

Highlights:
- Hazelcast entry search is `keySet()`-based and sample-limited (`ESTIMATED`)
- The per-map Hazelcast `hitRatio` returns `null` when counters are inconsistent across nodes (it used to be able to exceed 1)
- Redis Cluster (`CLUSTER INFO/NODES`) is not supported
- `ddl-auto: update` is unreliable, migrations are done by hand
- No Vault integration
- TLS support exists in the code but has not been verified in a TLS environment

---

## 10. Development Notes

### Schema management

`spring.jpa.hibernate.ddl-auto: update` in the `dev` profile, `validate` in `prod`.
`update` usually adds new columns but is **unreliable**: it misses narrowed column types,
index and constraint changes. The SQL shipped with the repo (section 4.4) has to be run by
hand. Flyway/Liquibase is recommended and out of scope for now.

### Tests

```bash
mvn clean test
```

GitHub Actions runs `mvn verify` and the frontend lint + build on every push to `main`/`claude` and on pull requests.

109 tests in two layers:

| Layer | Scope |
|---|---|
| Unit | INFO parser, keyspace parser, CPU delta, hit ratio, cursor pagination, metric status mapping, HZ partition aggregation, sampling, masking, Prometheus parser |
| Integration | Redis standalone, Redis ACL + permission denial, connection loss, HZ multi-member, protection layer (timeout / STALE / bulkhead / rate limit) |

**Docker requirement.** The Redis integration tests start a real `redis:7-alpine` container
with Testcontainers. Without Docker these classes are **skipped** thanks to
`@Testcontainers(disabledWithoutDocker = true)` and the build does not fail. The Hazelcast
tests use an embedded member and do not need Docker.

**Running from IntelliJ.** The surefire `environmentVariables` block in `pom.xml` only
applies through Maven. If you run tests from IntelliJ, add the following to
Run/Debug Configurations → Templates → JUnit → Environment variables, otherwise
`contextLoads` and the ACL test fail:

```
JWT_SECRET=ucmd-test-secret-key-min-32-characters-long
UCMD_BOOTSTRAP_USERNAME=test-admin
UCMD_BOOTSTRAP_PASSWORD=test-admin-pass
UCMD_TEST_REDIS_PASSWORD=ucmdTestPass
UCMD_TEST_REDIS_LIMITED_PASSWORD=limitedTestPass
```

The test profile uses H2 in memory (`src/test/resources/application-test.yml`); PostgreSQL
does not need to be running.

The parsers in `RedisMonitor` and `HazelcastMonitor` are `private`. Tests reach them with
`ReflectionTestUtils.invokeMethod`; visibility was not loosened in production code for the
sake of tests.

There is currently no CI workflow in the repo.

### Profiles

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

TLS is mandatory and Swagger is disabled in the prod profile. If no profile is given, `dev` runs.

### `open-in-view`

`false`: it stops lazy loading from leaking into the HTTP layer. Repository calls finish at
the service boundary.

### Prometheus scrape dependency

**All** of Hazelcast's map, operation, event, JVM and partition metrics come from the
jmx_exporter scrape; the Hazelcast Java client does not expose them. If an instance's
`metrics_url` column is empty, the related metrics return `UNAVAILABLE`: no fake zeros are produced.

The parser lives in the `com.ucmd.monitor.prometheus` package and is label-aware: it
separates fields by the `prefix` label (`queuesize` exists under both `operation` and
`executor.internal`) and decodes the escaped object name inside `tag0`.

### Log levels

`dev`: `com.ucmd` DEBUG. `prod`: root WARN, `com.ucmd` INFO.
No log line containing a key name, prefix or pattern should be left behind: the masking
rule applies to logs too.

---

## License

MIT. See [`LICENSE`](LICENSE).
