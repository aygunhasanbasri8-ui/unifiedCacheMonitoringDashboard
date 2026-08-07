# ADR-0007: Okuma uçlarında POST kullanımı

**Durum:** Kabul edildi
**Tarih:** 2026-08-06
**Karar sahibi:** Mustafa Bey (direktif)

## Bağlam

UCMD'nin metrik ve key analizi uçları veri **okur**, değiştirmez. REST konvansiyonu
bu uçlar için `GET` bekler:

```
GET /v1/instances/5/health
GET /v1/instances/5/keys?prefix=ucmd:merchant&cursor=0&count=50&matchMode=CONTAINS
```

Projede bunun yerine `POST` kullanılmasına karar verildi:

```
POST /v1/instances/5/health
POST /v1/instances/5/keys     { "prefix": "...", "cursor": "0", "count": 50, ... }
```

Bu, REST konvansiyonundan bilinçli bir sapmadır ve gerekçesi yazılı olmalıdır.

## Karar

Tüm metrik ve key analizi uçları `@PostMapping` kullanır. Parametreler query string
yerine request body'de taşınır.

**İstisnalar** (GET kalır):
- `GET /v1/health`, `/v1/health/app`, `/v1/health/collection` — parametresiz canlılık
- `GET /v1/audit` — sayfalama parametreleri basit ve hassas değil
- `GET /v1/admin/users` — parametresiz liste

REST uyumu adına bu uçlar GET'e çevrilmeyecektir.

## Gerekçe

**1. Hassas parametrelerin URL'de taşınmaması.** Key arama parametreleri üye işyeri
numarası içerebilir (`prefix=ucmd:merchant:88123`). URL'ler erişim loglarına, proxy
loglarına, tarayıcı geçmişine ve `Referer` header'ına düşer. Fintech ortamında bu
kabul edilemez. Request body bu yolların hiçbirine yazılmaz.

Bu gerekçe, projedeki key maskeleme kararıyla (ADR-0010) aynı endişeden doğuyor:
key adı hassas veridir.

**2. Karmaşık parametre yapısı.** `SearchKeysRequest` beş alan taşıyor (`prefix`,
`cursor`, `count`, `matchMode`, `mapName`); `SnapshotQueryRequest` iki. Query string'de
tip dönüşümü ve varsayılan değer yönetimi el yordamıyla yapılırdı. Body ile Jackson
doğrulaması ve DTO varsayılanları devreye giriyor.

**3. URL uzunluk sınırı.** Prefix 512 karaktere kadar kabul ediliyor; cursor değerleri
uzun olabiliyor. Query string'de bazı proxy'lerde sınıra takılma riski var.

**4. Tekdüzelik.** Bazı okuma uçlarının GET, bazılarının POST olması (parametre
karmaşıklığına göre) frontend'de tutarsızlık yaratırdı. Tek kural: metrik/analiz
uçları POST.

**Kabul edilen sapma:** POST semantik olarak "idempotent değil" ima eder. Bu uçlar
idempotenttir ve yan etkisizdir; sapmanın farkındayız.

**Elenen alternatif — `GET` + `X-Search-Params` header'ı:** Standart dışı, cache
davranışı belirsiz, header boyut sınırları var.

**Elenen alternatif — sadece key uçları POST, metrikler GET:** Tutarsızlık yaratır;
ayrıca metrik uçlarına ileride parametre eklenebilir (zaman aralığı, node filtresi).

## Sonuçlar

**Artılar**
- Hassas parametreler URL'lerde ve erişim loglarında görünmüyor
- Karmaşık istek nesneleri doğal biçimde taşınıyor
- Tüm okuma uçları tek kalıp; frontend istemcisi tekdüze

**Eksiler / kabul edilen ödünler**
- **HTTP cache kullanılamıyor.** Tarayıcı ve CDN POST yanıtlarını önbelleğe almaz.
  Bu yüzden cache sunucu tarafında Caffeine ile yapılıyor (ADR-0008)
- Tarayıcı adres çubuğundan test edilemiyor; `curl`/Postman/Swagger gerekiyor
- REST'e aşina geliştiriciler için sürpriz; bu ADR onlar için yazıldı
- Swagger'da "monitoring" grubu POST uçlarla dolu; salt-okunur olduğu açıklama
  metniyle belirtiliyor
- Idempotent olmayan metot kullanıldığı için otomatik retry yapan istemciler
  tereddüt edebilir (pratikte sorun değil, uçlar yan etkisiz)
