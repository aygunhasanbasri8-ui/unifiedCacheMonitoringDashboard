# ADR-0015: Eşik alarm motoru

**Durum:** Kabul edildi
**Tarih:** 2026-10-24

## Bağlam

UCMD bir sorunu gösteriyor ama haber vermiyordu: bellek dolarken ya da bir instance
düştüğünde birinin o an paneli açık tutması gerekiyordu. Metrikler zaten 30 saniyede
bir `SnapshotScheduler` tarafından toplanıp `metric_snapshots`'a yazılıyor.

## Karar

**Değerlendirme toplanan snapshot üzerinde yapılır.** `SnapshotScheduler` her turdan
sonra `AlertService.evaluate()` çağırır. Alarm için izlenen sisteme ek istek gitmez;
salt-okunur ilke ([ADR-0001](ADR-0001-salt-okunur-ilkesi.md)) ve koruma katmanı
([ADR-0006](ADR-0006-koruma-katmani-resilience4j.md)) aynen geçerli.

**Kural = metrik + operatör + eşik + süre.** "Bellek %85'in üstünde ve 5 dk sürüyor"
gibi. `for-minutes` boyunca kesintisiz sürmeyen ihlal alarm üretmez; tek bir sıçrama
gürültü olarak kalır. Kurallar şimdilik `ucmd.alerts.rules` altında, yapılandırmada.

**Durum modeline uyum** ([ADR-0003](ADR-0003-metricstatus-durum-modeli.md)): değeri
hesaplanamayan metrik (maxmemory tanımsız, hit oranı `null`, ilk eviction turu)
karar üretmez, alarm durumu olduğu gibi kalır. Erişilemeyen instance'ın sıfırlanmış
alanları "bellek %0, düzeldi" diye yorumlanmaz.

**Gürültü bastırma:** Bir alarm başladığında ve düzeldiğinde bir kez bildirilir;
sürdükçe `repeat-interval-minutes` (varsayılan 60) aralıkla hatırlatılır. Hatırlatma
geçmişe yeni olay olarak yazılmaz.

**Kanallar:** Telegram, Slack (incoming webhook), genel JSON webhook ve e-posta
(`spring.mail.host` tanımlıysa). Gönderim sanal thread'de yapılır, yavaş bir webhook
toplama turunu bekletmez. Token ve adresler env'den okunur, log'a yalnızca HTTP
durum kodu düşer.

**Kayıt:** Başlayan/düzelen olaylar `alert_events` tablosunda 90 gün; ayrıca
`ALERT_FIRING` / `ALERT_RESOLVED` olarak denetim kaydına (ADR-0012) düşer.

## Sonuçlar

- (+) Ek bağımlılık yok (JDK HttpClient), yalnız e-posta için `spring-boot-starter-mail`
- (+) Değerlendirme saf mantık; sahte saatle birim testi var (`AlertServiceImplTest`)
- (−) Alarm durumu bellekte; uygulama yeniden başlayınca süren alarm bir kez daha
  "başladı" olarak bildirilir. Çok replikalı kurulumda her replika ayrı bildirir
- (−) Kural eklemek yeniden başlatma ister; yönetim ekranı gerekirse kurallar DB'ye
  taşınır, değerlendirme tarafı kuralın kaynağını bilmiyor
