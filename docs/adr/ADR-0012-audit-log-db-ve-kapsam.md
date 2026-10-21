# ADR-0012: Audit log'un DB'de tutulması ve kapsam kararı

**Durum:** Kabul edildi
**Tarih:** 2026-08-06

## Bağlam

İlk uygulamada denetim kaydı bellekte tutuluyordu: `ArrayDeque` + `ReentrantLock`,
`max-entries: 5000` ile sınırlı halka tampon.

Üç problem:
1. **Kalıcı değil.** Uygulama yeniden başlatıldığında tüm kayıtlar siliniyordu.
   Bir güvenlik incelemesinde "dün saat kaçta kim giriş yaptı" sorusu cevapsız kalırdı
2. **Kullanıcı bilgisi yoktu.** Tüm kayıtlarda `username: "SYSTEM"` yazıyordu; kimin
   ne yaptığı belli değildi. IP adresi hiç tutulmuyordu
3. **Kapsam belirsizdi.** Hangi işlemlerin loglanacağına dair kural yoktu

Kapsam sorusu kritikti: dashboard 5 saniyede bir 8 kart yeniliyorsa, tek kullanıcı
dakikada ~96 metrik çağrısı üretir. Bunları loglamak günde on binlerce satır demek.

## Karar

**Depolama:** Tamamen veritabanı. In-memory yapı kaldırıldı.
`audit_logs` tablosu, `timestamp` / `username` / `action` üzerinde index.

**Kapsam — loglanan:**
- Kimlik doğrulama: `LOGIN_SUCCESS`, `LOGIN_FAILED`, `LOGIN_ERROR`, `LOGOUT`
- Mutasyonlar: `INSTANCE_CREATED/UPDATED/DELETED`, `USER_CREATED`, `USER_DELETED`
- Key erişimi: `KEY_SEARCH`, `KEY_TOP_N`, `KEY_TTL_QUERY`

**Kapsam — loglanmayan:**
Metrik uçları (`/health`, `/resources`, `/connections`, `/data-stats`, `/efficiency`,
`/replication`, `/persistence`, `/cluster`, `/map-stats`, `/operation-stats`),
snapshot ve history uçları.

**Arama terimleri maskeli saklanır.** `KEY_SEARCH` kaydındaki prefix ve
`KEY_TTL_QUERY` kaydındaki key `KeyMasker.maskForLog()` üzerinden geçer (ADR-0010).

**Kullanıcı ve IP:** `AuditContext` isteğin `currentUser` attribute'undan kullanıcı
adını, `X-Forwarded-For` başlığının ilk değerinden (yoksa `getRemoteAddr()`) IP'yi
çözer.

**Saklama:** 90 gün. `@Scheduled(cron = "0 0 3 * * *")` ile her gece temizlik.

**Hata davranışı:** Denetim yazımı ana işlemi **asla** kesmez. `persist()` tüm
exception'ları yakalar, loglar ve yutar.

## Gerekçe

**Neden metrik uçları loglanmıyor?** Denetim değeri düşük, maliyeti yüksek. "Kullanıcı
X dashboard'a baktı" bilgisi bir güvenlik incelemesinde işe yaramaz; tabloyu şişirip
gerçek olayları (giriş denemeleri, silme işlemleri) gürültüye boğar. Key erişimi
ise loglanıyor çünkü **hangi key'lerin sorgulandığı** hassas bilgidir.

**Neden arama terimleri maskeli?** Denetim kaydı kalıcı ve daha geniş erişimlidir.
Maskelenmemiş prefix saklamak, denetim tablosunu yeni bir sızıntı yüzeyine çevirirdi.
Maskeli hali "hangi desen arandı" sorusunu yine cevaplıyor.

**Neden 90 gün?** Tipik iç denetim penceresi. Snapshot verisi 7 gün tutuluyor (hacim
çok yüksek, denetim değeri yok); denetim kaydı düşük hacimli ve yüksek değerli.

**Neden denetim hatası yutuluyor?** Denetim tablosu dolarsa veya DB geçici olarak
erişilemezse kullanıcı **giriş yapamamalı mı?** Hayır. İzleme aracının denetim
altyapısı, izleme işlevini engellememeli. Ödün: denetim kaydı kayıpsız (lossless)
değildir — yazılamazsa yalnızca uygulama loguna hata düşer.

**Elenen alternatif — hem bellek hem DB (4b):** İki kaynak arasında tutarsızlık
riski, ek karmaşıklık. DB zaten sayfalı sorguyu daha iyi yapıyor.

**Elenen alternatif — ayrı log dosyası / SIEM:** Ayrı bir altyapı gerektirir; bu
fazda kapsam dışı. DB tablosu SIEM'e beslenebilir.

## Sonuçlar

**Artılar**
- Kayıtlar yeniden başlatmaya dayanıklı
- Gerçek kullanıcı adı ve IP kaydediliyor (doğrulandı: `LOGIN_FAILED` /
  `result: DENIED` / gerçek username / IP dolu)
- Sayfalı, `action` ve `username` ile filtrelenebilir sorgu (`GET /v1/audit`, ADMIN)
- Metrik uçları kayıt üretmiyor (doğrulandı: 3 metrik çağrısı sonrası
  `totalElements` değişmedi)

**Eksiler / kabul edilen ödünler**
- Denetim kaydı kayıpsız değil; DB hatası sessizce yutuluyor. Bilinçli tercih
- Her mutasyon isteğine bir INSERT ekleniyor; yazma yükü arttı (hacim düşük olduğu
  için kabul edilebilir)
- Yol/rol değişikliklerinde denetim çağrısı eklemek geliştiricinin sorumluluğunda;
  otomatik değil (AOP ile yapılabilirdi, karmaşıklık nedeniyle seçilmedi)
- `GET /v1/audit` yanıt tipi `List` yerine `Page` oldu — frontend kırılması
