"""
UCMD Hazelcast Veri Besleyici
Hazelcast cluster'ına gerçekçi IMap'ler oluşturur ve düzenli günceller.
UCMD'nin Hazelcast tarafını izleyebilmesi için test verisi sağlar.
"""

import hazelcast
import random
import time
import json
from datetime import datetime

CLUSTER_NAME = "dev"
CLUSTER_MEMBERS = ["localhost:5701"]

CITIES = ["Istanbul", "Ankara", "Izmir", "Bursa", "Antalya"]
SECTORS = ["Market", "Restoran", "Giyim", "Elektronik", "Akaryakit"]
STATUSES = ["ACTIVE", "SUSPENDED", "PENDING", "PASSIVE"]

UPDATE_INTERVAL = 60   # saniye


def log(msg):
    print(f"[{datetime.now().strftime('%H:%M:%S')}] {msg}")


def main():
    log("Hazelcast'e bağlanılıyor...")
    client = hazelcast.HazelcastClient(
        cluster_name=CLUSTER_NAME,
        cluster_members=CLUSTER_MEMBERS,
    )
    log(f"✓ Bağlandı: {CLUSTER_NAME}")

    # Farklı amaçlı map'ler (UCMD'de ayrı ayrı görünecek)
    merchants = client.get_map("ucmd-merchants").blocking()
    sessions = client.get_map("ucmd-sessions").blocking()
    city_index = client.get_map("ucmd-city-index").blocking()
    sector_index = client.get_map("ucmd-sector-index").blocking()
    risk_scores = client.get_map("ucmd-risk-scores").blocking()

    log("Map'ler hazır: ucmd-merchants, ucmd-sessions, ucmd-city-index, "
        "ucmd-sector-index, ucmd-risk-scores")

    cycle = 0
    try:
        while True:
            cycle += 1
            log(f"--- Tur {cycle} ---")

            # Üye işyeri kayıtları (50 adet, her turda bazıları güncellenir)
            updated = 0
            for i in range(1, 51):
                code = f"UIY{1000 + i}"
                if cycle == 1 or random.random() < 0.2:
                    merchants.put(code, json.dumps({
                        "code": code,
                        "name": f"Isyeri {i}",
                        "city": random.choice(CITIES),
                        "sector": random.choice(SECTORS),
                        "status": random.choices(STATUSES, weights=[70, 10, 12, 8])[0],
                        "commission": round(random.uniform(1.2, 3.5), 2),
                        "volume": round(random.uniform(5000, 250000), 2),
                    }))
                    updated += 1
            log(f"  ✓ ucmd-merchants: {updated} kayıt güncellendi (toplam {merchants.size()})")

            # Şehir ve sektör indeksleri
            for city in CITIES:
                city_index.put(city, json.dumps({
                    "city": city,
                    "count": random.randint(5, 20),
                    "totalVolume": round(random.uniform(100000, 900000), 2),
                }))
            for sector in SECTORS:
                sector_index.put(sector, json.dumps({
                    "sector": sector,
                    "count": random.randint(3, 15),
                    "avgCommission": round(random.uniform(1.5, 3.0), 2),
                }))
            log(f"  ✓ İndeksler: {city_index.size()} şehir, {sector_index.size()} sektör")

            # Risk skorları
            for i in range(1, 51):
                risk_scores.put(f"UIY{1000 + i}", random.randint(1, 100))
            log(f"  ✓ ucmd-risk-scores: {risk_scores.size()} kayıt")

            # Oturumlar — TTL'li (60-180 sn), expire olup düşecekler
            new_sessions = random.randint(3, 8)
            for _ in range(new_sessions):
                sid = f"SES{random.randint(100000, 999999)}"
                sessions.put(sid, json.dumps({
                    "sessionId": sid,
                    "user": f"user{random.randint(1, 200)}",
                    "startedAt": datetime.now().isoformat(),
                }), ttl=random.randint(60, 180))
            log(f"  ✓ ucmd-sessions: {new_sessions} yeni oturum (toplam {sessions.size()})")

            # Rastgele okuma trafiği (hit üretir)
            for _ in range(20):
                merchants.get(f"UIY{1000 + random.randint(1, 50)}")
                risk_scores.get(f"UIY{1000 + random.randint(1, 50)}")

            log(f"  Toplam entry: merchants={merchants.size()}, "
                f"sessions={sessions.size()}, risk={risk_scores.size()}")
            log(f"  Sonraki tur {UPDATE_INTERVAL} sn sonra...\n")

            time.sleep(UPDATE_INTERVAL)

    except KeyboardInterrupt:
        log("\nBesleyici durduruldu.")
    finally:
        client.shutdown()


if __name__ == "__main__":
    main()