# ADR-0011: Yazma uçlarının `/v1/admin/**` altına ayrılması

**Durum:** Kabul edildi
**Tarih:** 2026-08-06

## Bağlam

`InstanceController` hem okuma hem yazma uçlarını barındırıyordu:

```
POST   /v1/instances/list        (okuma)
POST   /v1/instances/{id}/detail (okuma)
POST   /v1/instances             (create)
PUT    /v1/instances/{id}        (update)
DELETE /v1/instances/{id}        (delete)
```

Sınıf seviyesinde `@RequireRole("MONITOR")` vardı — yani **MONITOR rolündeki bir
kullanıcı instance kaydı silebiliyordu.** Bu bir yetki açığıydı.

Ayrıca salt-okunur ilke ([ADR-0001](ADR-0001-salt-okunur-ilkesi.md)) açıkça şunu istiyor: *"UCMD yalnızca read-only endpoint'ler sunmalıdır.
Swagger/OpenAPI dokümanında POST, PUT, PATCH ve DELETE endpoint'leri bulunmamalıdır."*
Aynı controller'da karışık durdukları için üretilen API dokümanı bu beklentiyi
karşılayamıyordu.

Not: Okuma uçları da POST kullanıyor (ADR-0007). Bu ilkenin kastettiği "yazma metodu
yok" değil, "**yazma işlemi** yok"tur; ayrım yol ve rol ile yapılır.

## Karar

Yazma uçları ayrı controller'lara ve ayrı yol ağacına taşındı:

| Eski | Yeni | Rol |
|---|---|---|
| `POST /v1/instances` | `POST /v1/admin/instances` | ADMIN |
| `PUT /v1/instances/{id}` | `PUT /v1/admin/instances/{id}` | ADMIN |
| `DELETE /v1/instances/{id}` | `DELETE /v1/admin/instances/{id}` | ADMIN |
| `GET /v1/users` | `GET /v1/admin/users` | ADMIN |
| `POST /v1/users` | `POST /v1/admin/users` | ADMIN |
| `DELETE /v1/users/{username}` | `DELETE /v1/admin/users/{username}` | ADMIN |

- `controller/admin/AdminInstanceController` ve `controller/admin/AdminUserController`
  oluşturuldu; ikisi de sınıf seviyesinde `@RequireRole("ADMIN")`
- `controller/UserController` silindi
- `InstanceController`'da yalnızca `POST /list` ve `POST /{id}/detail` kaldı
- Tüm yazma işlemleri denetim kaydı üretiyor (`INSTANCE_CREATED`, `USER_DELETED`, ...)

**Savunma derinliği:** `AuthInterceptor`, `@RequireRole` kontrolünden **önce** yol
tabanlı bir kontrol yapar — `/v1/admin/` içeren her yol için kullanıcı ADMIN değilse
403 döner. Yeni bir admin controller'a anotasyon eklemeyi unutan geliştirici bile
açık bırakamaz.

**Swagger ayrımı:** İki grup tanımlandı. `monitoring` grubu
(`/v1/instances/**`, `/v1/health/**`, `/v1/auth/**`) teslim edilen dokümandır ve
içinde hiç PUT/DELETE/PATCH yoktur (doğrulandı). `admin` grubu yazma uçlarını gösterir.

## Gerekçe

**Neden yol ile ayrım, sadece rol anotasyonu değil?** Anotasyon unutulabilir. Yol
tabanlı kontrol ikinci bir kapı sağlıyor. Ayrıca yol ayrımı sayesinde ileride
`/v1/admin/**` ağ seviyesinde (reverse proxy, ayrı port, IP kısıtı) de korunabilir.

**Neden ADMIN, MONITOR değil?** Instance kaydını silmek izleme kapsamını değiştirir;
bir instance sessizce izlemeden çıkabilir. Kullanıcı yönetimi zaten yönetimsel iştir.

**Neden `GET /v1/admin/users` GET kaldı?** Parametresiz liste, hassas parametre yok;
ADR-0007'nin gerekçeleri geçerli değil.

**Elenen alternatif — yazma uçlarını tamamen kaldırıp yapılandırmayı yml'e taşımak:**
Instance ekleme/çıkarma çalışma zamanında gerekiyor; her değişiklik için yeniden
başlatma kabul edilemez.

## Sonuçlar

**Artılar**
- MONITOR rolü artık hiçbir şeyi değiştiremiyor (doğrulandı: MONITOR ile
  `POST /v1/admin/instances` → 403, `DELETE /v1/admin/users/x` → 403)
- Teslim edilen Swagger dokümanı salt-okunur API beklentisini (ADR-0001) karşılıyor
- Tüm mutasyonlar denetim kaydında; kim ne zaman ne değiştirdi izlenebiliyor
- Yol ayrımı ileride ağ seviyesinde kısıtlamaya imkân veriyor

**Eksiler / kabul edilen ödünler**
- **Frontend kırılıyor.** Altı endpoint yolu değişti; frontend güncellemesi zorunlu
- Eski yollara yapılan istekler 404 · `endpoint-not-found` alıyor. Geriye dönük
  uyumluluk katmanı konmadı — sessizce çalışmaya devam etmesindense açıkça kırılması
  tercih edildi
- İki yerde rol kontrolü var (anotasyon + interceptor yol kontrolü); biri değişirse
  diğeri de gözden geçirilmeli
