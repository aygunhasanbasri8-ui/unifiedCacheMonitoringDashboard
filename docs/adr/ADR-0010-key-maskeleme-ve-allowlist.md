# ADR-0010: Key maskeleme ve prefix/map allowlist

**Durum:** Kabul edildi
**Tarih:** 2026-08-06

## Bağlam

UCMD'nin key arama özelliği, izlenen cache'teki key **adlarını** kullanıcıya gösterir.
Bu projede key adları hassas veri taşıyor:

```
ucmd:merchant:88123:commission   → 88123 numaralı üye işyerinin komisyon oranı var
ucmd:merchant:88123:risk         → aynı işyerinin risk skoru var
ucmd:terminal:4471:lasttx        → terminal kimliği
```

Key adı tek başına bile bilgi sızdırır: hangi üye işyerlerinin sistemde olduğu,
hangilerinin risk kaydı bulunduğu. Değer görüntüleme kapsam dışı bırakılmış olsa
bile ad görüntüleme bir risktir.

İkinci risk: key adları log dosyalarına düşüyordu (`Scanned N keys (pattern '...')`).
Log dosyaları farklı bir erişim rejimine tabidir.

## Karar

### Maskeleme

`KeyMasker` bileşeni, **3+ haneli rakam dizilerini** `***` ile değiştirir:

```
ucmd:merchant:88123:commission  →  ucmd:merchant:***:commission
UIY1057                         →  UIY***
```

İki metot:
- `mask(key)` — `ucmd.security.key-masking.enabled` ayarına uyar
- `maskForLog(key)` — **her zaman** maskeler; log ve denetim kaydı için

Varsayılan: **açık**. **Rol istisnası yok** — ADMIN de maskeli görür.

İstisna: `KeyTtlResponse.key` maskelenmez. Kullanıcı sorguladığı key'i geri görmeli;
denetim kaydına maskeli yazılır.

### Allowlist

| Ayar | Boşsa | Doluysa ihlal edilirse |
|---|---|---|
| `ucmd.security.allowed-prefixes` | Kısıt yok | **400** · `invalid-prefix` |
| `ucmd.security.allowed-maps` | Kısıt yok | **403** · `map-not-allowed` |

Prod profilinde prefix listesi `ucmd:merchant`, `ucmd:terminal`, `ucmd:transaction`,
`ucmd:stats` ile sınırlıdır.

## Gerekçe

**Neden 3+ haneli rakam kuralı?** Kimlik taşıyan alanlar (üye işyeri kodu, terminal
kimliği, işlem numarası) bu projede her zaman 3 haneden uzun sayılardır. Yapısal
kısımlar (`merchant`, `commission`, `daily`) harflerden oluşur ve korunur — operatör
key'in **ne olduğunu** görür, **kime ait olduğunu** görmez. Bu, izleme değerini
koruyup gizliliği sağlayan denge.

**Neden ADMIN istisnası yok?** İstisna olsaydı denetim kaydında "ADMIN gördü, MONITOR
görmedi" gibi rol bağımlı bir iz oluşurdu; bir kaydın maskeli olup olmadığı okuyanın
rolüne bağlı olurdu. Denetim izinin tek anlamı olması için istisna konmadı. Ayrıca
ADMIN hesabının ele geçirilmesi durumunda ek bir sızıntı yüzeyi doğmuyor.

**Neden `maskForLog` ayrı?** Maskeleme kapatılabilir bir ayar (geliştirme kolaylığı
için). Ama log dosyaları ve denetim kaydı kalıcıdır ve farklı ellere geçebilir;
oradaki maskeleme ayardan bağımsız olmalı.

**Neden prefix ihlali 400, map ihlali 403?** Prefix kuralı bir girdi kısıtıdır
("prefix en az 3 karakter ve şu listede olmalı") — istemci isteği düzeltebilir.
Map allowlist bir **yetki** kısıtıdır: map var, geçerli, ama bu kuruluma o map'e
bakma izni verilmemiş. 403 doğru semantik.

**Neden allowlist boşken kısıt yok?** Geliştirme ortamını kırmamak için. Prod
profilinde liste doludur.

**Elenen alternatif — tüm key adını hash'lemek:** İzleme değerini yok ederdi;
operatör hangi desenin şiştiğini göremezdi.

**Elenen alternatif — maskelemeyi sadece response'ta yapmak:** Loglar açıkta kalırdı.

## Sonuçlar

**Artılar**
- Key adları hem yanıtta hem logda maskeli (doğrulandı: log dosyasında 3+ haneli
  merchant/terminal kimliği bulunamadı)
- Desen bilgisi korunduğu için izleme değeri kaybolmadı
- Allowlist ile prod'da erişim yüzeyi daraltılabiliyor

**Eksiler / kabul edilen ödünler**
- Maskeli key ile doğrudan sorgulama yapılamaz; operatör `ucmd:merchant:***:risk`
  görüp tam key'i bilmiyorsa TTL sorgusu yapamaz. Bilinçli
- Sıralama maskelemeden **önce** yapılmak zorunda: maskelenmiş ad `startsWith(prefix)`
  karşılaştırmasını bozar. `RedisMonitor.scanKeys` bu sırayı gözetiyor
- Prometheus sorguları **ham** map adıyla yapılmalı, maskeli adla değil.
  `HazelcastMonitor.getMapStats` bu ayrımı korur; karıştırılırsa metrikler sessizce
  `null` döner
- 3 haneden kısa kimlikler maskelenmez (örn. `ucmd:merchant:42:risk`). Bu projedeki
  veri formatında görülmüyor, ama kural veriye bağımlı bir varsayım taşıyor
