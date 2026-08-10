# ADR-0001: Salt-okunur ilkesi ve komut kısıtlaması

**Durum:** Kabul edildi
**Tarih:** 2026-08-06

## Bağlam

UCMD, üretim ortamındaki Redis ve Hazelcast instance'larına bağlanan bir izleme
aracıdır. İzlenen sistemler ödeme akışının sıcak yolunda yer alır: üye işyeri komisyon
oranları, terminal durumları ve risk skorları buradan okunur. Bir monitoring aracının
yanlışlıkla veri değiştirmesi veya silmesi doğrudan finansal işlem hatasına dönüşür.

Ayrıca bazı Redis komutları veri değiştirmese bile sunucuyu bloklar: `KEYS *` O(N)
tam tarama yapar ve tek thread'li Redis'te tüm istemcileri bekletir; `DEBUG SLEEP`
sunucuyu dondurur; `FLUSHALL` veriyi siler.

## Karar

UCMD **hiçbir koşulda** yazma veya durum değiştirme komutu çalıştırmaz.

İzin verilen Redis komutları, tam liste:
`PING`, `INFO`, `SCAN`, `TYPE`, `TTL`, `PTTL`, `MEMORY USAGE`.

İzin verilen Hazelcast API'leri, tam liste:
`IMap.size()`, `IMap.keySet()`, `IMap.getEntryView()`, `getDistributedObjects()`,
`getCluster()`, `getPartitionService()`, `getLifecycleService()`.

`KEYS *` yasaktır; key taraması yalnızca cursor tabanlı `SCAN` ile yapılır ve
prefix zorunludur.

Bu kısıt tek savunma hattı olarak bırakılmaz: sunucu tarafında read-only ACL
kullanıcısı kurulumu `docs/redis-acl-setup.md`'de belgelenmiştir.

## Gerekçe

**Neden uygulama katmanında sabitlendi?** Yapılandırmayla açılıp kapanabilen bir
"read-only mod" olsaydı, yanlış yapılandırma tek başına veri kaybına yol açabilirdi.
Komut listesi koda gömülüdür; genişletmek kod değişikliği ve gözden geçirme gerektirir.

**Neden ACL'e güvenilmiyor?** ACL doğru çözümdür ama her ortamda kurulu olmayabilir
(geliştirme, staging). Uygulama katmanı kısıtı her ortamda çalışır; ACL ikinci
katmandır. İkisi birlikte savunma derinliği sağlar.

**Elenen alternatif — `redis-cli --readonly` benzeri bir bayrak:** Lettuce'ta böyle
bir mekanizma yok; ayrıca komutun tehlikeli olup olmadığı çağrı yerinde belli
olmadığından hata yapmaya açık.

**Elenen alternatif — Hazelcast'te `IMap.entrySet()` ile tam okuma:** Veri değiştirmez
ama tüm veriyi client'a çeker; §2.2'nin "izlenen sistemi yorma" ilkesini ihlal eder.
Bunun yerine sınırlı `keySet()` örneklemesi tercih edildi (bkz. ADR-0010 ve bilinen
sınırlar).

## Sonuçlar

**Artılar**
- İzlenen sistemin bütünlüğü kod düzeyinde garanti altında
- Güvenlik incelemesi kolay: tek dosyada sabit komut listesi
- ACL'e geçiş kolaylaştı; `PERMISSION_DENIED` statüsü ile yetki hataları ayırt ediliyor

**Eksiler / kabul edilen ödünler**
- Bazı metrikler doğrudan alınamıyor. Örneğin Hazelcast `LocalMapStats` client
  modunda yoktur; map istatistikleri Prometheus scrape üzerinden dolaylı alınıyor
- Redis `MEMORY USAGE` key başına örneklem yapar, tam boyut değil — sonuçlar
  `ESTIMATED` işaretleniyor
- Cache'i "ısıtma" veya test verisi yazma gibi kolaylıklar UCMD üzerinden yapılamaz;
  bilinçli bir kısıttır
