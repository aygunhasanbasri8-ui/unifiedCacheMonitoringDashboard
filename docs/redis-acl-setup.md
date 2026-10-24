# UCMD — Redis ACL ve Hazelcast Read-Only Erişim Kurulumu

Güvenlik gereği UCMD, izlediği cache sistemlerine **yalnızca okuma** yetkisiyle
bağlanmalıdır. Uygulama katmanında salt-okunurluk zaten zorlanıyor (`RedisMonitor`
sadece `PING`, `INFO`, `SCAN`, `TYPE`, `PTTL`, `TTL`, `MEMORY USAGE` çağırıyor),
fakat bu **tek savunma hattı olmamalıdır**. Sunucu tarafında ACL ile de kısıtlanır.

Bu doküman kod değil, operasyon rehberidir.

---

## 1. UCMD için ayrı Redis kullanıcısı

`default` kullanıcısı kullanılmaz. UCMD kendi kimliğiyle bağlanır — böylece
`ACL LOG` ve `CLIENT LIST` çıktılarında UCMD trafiği ayırt edilebilir.

```
ACL SETUSER ucmd_monitor on >GUCLU_BIR_PAROLA \
    ~ucmd:* \
    resetchannels \
    -@all \
    +ping +info +scan +type +ttl +pttl +memory|usage
```

Parçaların anlamı:

| Parça | Anlamı |
|---|---|
| `on` | Kullanıcı aktif |
| `>PAROLA` | Parola belirle (prod'da secret manager'dan gelmeli) |
| `~ucmd:*` | Sadece `ucmd:` ile başlayan key'lere erişim |
| `resetchannels` | Pub/Sub kanallarına erişim yok |
| `-@all` | Önce tüm komutlar kapatılır (deny-by-default) |
| `+ping ...` | Sonra yalnızca gereken komutlar açılır |

`-@all` ile başlayıp tek tek komut açmak, yasaklıları tek tek saymaktan daha
güvenlidir — yeni Redis sürümünde eklenen bir komut otomatik olarak kapalı kalır.

### Komut allowlist'i

```
+ping           # erişilebilirlik kontrolü
+info           # tüm metrik bölümleri (server, memory, cpu, clients, stats,
                # keyspace, replication, persistence, cluster)
+scan           # cursor tabanlı key tarama — KEYS yerine
+type           # key tipi
+ttl +pttl      # kalan yaşam süresi
+memory|usage   # key başına bellek maliyeti (sadece MEMORY USAGE alt komutu)
```

`+memory|usage` yazımı önemlidir: `+memory` yazılırsa `MEMORY PURGE` ve
`MEMORY DOCTOR` da açılır. Alt komut seviyesinde yetki verilir.

### Açıkça yasaklı olması gerekenler

`-@all` ile zaten kapalıdırlar; aşağıdaki liste denetim/dokümantasyon amaçlıdır.
Mevcut bir kullanıcıyı sıkılaştırıyorsanız bunları açıkça kapatın:

```
-@write -@dangerous -config -module -eval -evalsha -script -debug
-shutdown -migrate -restore -flushdb -flushall -keys
```

| Komut | Neden yasak |
|---|---|
| `@write` | Salt-okunur ilkesi (SET / DEL / EXPIRE / RENAME …) |
| `@dangerous` | `KEYS`, `FLUSHALL`, `SHUTDOWN`, `MONITOR` vb. toplu tehlikeli grup |
| `KEYS` | O(N) tam tarama — izlenen sistemi kilitler |
| `FLUSHDB` / `FLUSHALL` | Veri kaybı |
| `CONFIG` | `CONFIG SET` ile çalışma zamanı ayarı değiştirilebilir |
| `MODULE` | Sunucuya kod yüklenebilir |
| `EVAL` / `EVALSHA` / `SCRIPT` | Lua ile ACL kısıtları aşılabilir |
| `DEBUG` | `DEBUG SLEEP` sunucuyu bloklar, `DEBUG SEGFAULT` çökertir |
| `SHUTDOWN` | Sunucuyu durdurur |
| `MIGRATE` / `RESTORE` | Veri dışarı taşınabilir / yazma yolu |

### Doğrulama

```
ACL GETUSER ucmd_monitor
ACL LIST
```

Bağlanıp deneyin — ilk üçü çalışmalı, son ikisi `NOPERM` hatası vermeli:

```
redis-cli -u redis://ucmd_monitor:PAROLA@localhost:6379 PING
redis-cli -u redis://ucmd_monitor:PAROLA@localhost:6379 INFO memory
redis-cli -u redis://ucmd_monitor:PAROLA@localhost:6379 SCAN 0 MATCH 'ucmd:*' COUNT 10
redis-cli -u redis://ucmd_monitor:PAROLA@localhost:6379 SET ucmd:test 1     # NOPERM beklenir
redis-cli -u redis://ucmd_monitor:PAROLA@localhost:6379 KEYS '*'            # NOPERM beklenir
```

`ACL LOG` reddedilen çağrıları gösterir — UCMD'nin salt-okunur kaldığını
periyodik olarak buradan denetleyin.

---

## 2. Örnek `users.acl` dosyası

`ACL SETUSER` çalışma zamanında etki eder ama yeniden başlatmada kaybolur.
Kalıcı yapılandırma için dosya kullanılır.

`redis/users.acl`:

```
# ── default kullanıcısı kapatılır ──
user default off

# ── UCMD izleme kullanıcısı (salt okunur) ──
user ucmd_monitor on >UCMD_PAROLASI ~ucmd:* resetchannels -@all +ping +info +scan +type +ttl +pttl +memory|usage

# ── Uygulama kullanıcısı (cache'i besleyen taraf, tam yetki) ──
user app_writer on >APP_PAROLASI ~ucmd:* resetchannels +@all -@dangerous -@admin
```

`user default off` satırı kritiktir: aksi halde parolasız `default` kullanıcısı
tam yetkiyle açık kalır ve ACL kurgusu anlamsızlaşır.

Parolalar dosyaya düz metin yazıldığı için `users.acl` dosya izinleri `600`
olmalı ve secret manager'dan üretilmelidir. Versiyon kontrolüne **girmemelidir**.

---

## 3. Docker'da uygulama

`docker-compose.yml` içindeki redis servisine `--aclfile` eklenir:

```yaml
  redis:
    image: redis:7
    container_name: redis
    command: >-
      redis-server
      --maxmemory 256mb
      --maxmemory-policy allkeys-lru
      --aclfile /etc/redis/users.acl
    volumes:
      - ./redis/users.acl:/etc/redis/users.acl:ro
    ports:
      - "6379:6379"
    networks:
      - ucmd-net
```

Dikkat: `--aclfile` kullanıldığında `requirepass` ve `user` direktifleri
`redis.conf` içinde **birlikte kullanılamaz**; tüm kullanıcı tanımı ACL
dosyasına taşınır.

Dosya salt-okunur (`:ro`) mount edilir — böylece `ACL SAVE` ile container
içinden değiştirilemez.

Uygulanışını doğrulayın:

```
docker exec -it redis redis-cli -u redis://ucmd_monitor:PAROLA@localhost:6379 ACL WHOAMI
```

### UCMD tarafındaki yapılandırma

ACL etkinleştirildiğinde UCMD'nin kullanıcı adı + parola ile bağlanması gerekir.
Şu an `RedisConnectionManager` yalnızca host/port ile `RedisURI` kuruyor —
ACL'e geçmeden önce kullanıcı adı/parola desteğinin eklenmesi gerekir.
Bu değişiklik bu fazın kapsamı dışındadır, teslim notlarına yazılmıştır.

---

## 4. Hazelcast read-only client permission

Hazelcast'te ACL karşılığı **client permission** yapılandırmasıdır ve
üye (member) tarafında `hazelcast.xml` içinde tanımlanır. Client permission'lar
Hazelcast **Enterprise** özelliğidir; open-source sürümde uygulanamaz.

```xml
<security enabled="true">
    <client-permissions>
        <!-- Cluster durumu ve metrik okuma -->
        <management-permission principal="ucmd_monitor">
            <endpoints>
                <endpoint>10.0.0.*</endpoint>
            </endpoints>
            <actions>
                <action>get</action>
            </actions>
        </management-permission>

        <!-- Tüm IMap'lerde yalnızca okuma -->
        <map-permission name="*" principal="ucmd_monitor">
            <endpoints>
                <endpoint>10.0.0.*</endpoint>
            </endpoints>
            <actions>
                <action>read</action>
                <action>create</action>   <!-- proxy oluşturma; veri yazma değil -->
            </actions>
        </map-permission>
    </client-permissions>
</security>
```

`actions` listesinde bulunmaması gerekenler: `put`, `remove`, `lock`,
`intercept`, `listen`, `destroy`. Bunlardan biri açık kalırsa UCMD teknik
olarak veri değiştirebilir hale gelir.

`create` aksiyonu gereklidir çünkü `IMap` proxy'si oluşturmak izin ister;
tek başına veri yazma yetkisi vermez.

`endpoint` değeri UCMD sunucusunun IP aralığıyla sınırlandırılmalıdır —
sızan bir kimlik bilgisinin ağ dışından kullanılmasını engeller.

Open-source Hazelcast kullanılıyorsa (bu projede olduğu gibi) tek koruma
uygulama katmanındadır: `HazelcastMonitor` yalnızca `size()`,
`getDistributedObjects()`, `getCluster()`, `getPartitionService()` çağırır —
hiçbir mutasyon API'si kullanmaz. Bu sınır teslim notlarına yazılmıştır.
