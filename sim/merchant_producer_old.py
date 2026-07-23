"""
UCMD Üye İşyeri Veri Üreticisi
- merchant_db veritabanını ve merchants tablosunu (yoksa) oluşturur
- 80 üye işyeri kaydı üretir (ilk çalıştırmada)
- Her 15 dakikada bir bazı kayıtları günceller (komisyon, hacim, durum, risk)
- Değişiklikleri log ekranında gösterir

"""

import psycopg2
from psycopg2 import sql
import random
import time
from datetime import datetime
from decimal import Decimal

# --- Bağlantı ayarları ---
PG_HOST = "localhost"
PG_PORT = 5432
PG_USER = "ucmd_user"
PG_PASSWORD = "ucmd_pass"
ADMIN_DB = "ucmd_db"        # merchant_db'yi oluşturmak için bağlanacağımız mevcut DB
TARGET_DB = "merchant_db"

MERCHANT_COUNT = 80
UPDATE_INTERVAL_SECONDS = 15 * 60   # 15 dakika

CITIES = ["Istanbul", "Ankara", "Izmir", "Bursa", "Antalya", "Adana", "Konya", "Gaziantep"]
SECTORS = ["Market", "Restoran", "Giyim", "Elektronik", "Akaryakit", "Eczane", "Kuyumcu", "Turizm"]
STATUSES = ["ACTIVE", "SUSPENDED", "PENDING", "PASSIVE"]


def log(msg):
    print(f"[{datetime.now().strftime('%H:%M:%S')}] {msg}")


def create_database_if_missing():
    """merchant_db yoksa oluştur (ucmd_db üzerinden bağlanarak)."""
    conn = psycopg2.connect(host=PG_HOST, port=PG_PORT, user=PG_USER,
                            password=PG_PASSWORD, dbname=ADMIN_DB)
    conn.autocommit = True   # CREATE DATABASE transaction içinde çalışmaz
    cur = conn.cursor()
    cur.execute("SELECT 1 FROM pg_database WHERE datname = %s", (TARGET_DB,))
    if cur.fetchone():
        log(f"Veritabanı '{TARGET_DB}' zaten mevcut.")
    else:
        cur.execute(sql.SQL("CREATE DATABASE {}").format(sql.Identifier(TARGET_DB)))
        log(f"✓ Veritabanı '{TARGET_DB}' oluşturuldu.")
    cur.close()
    conn.close()


def get_connection():
    return psycopg2.connect(host=PG_HOST, port=PG_PORT, user=PG_USER,
                            password=PG_PASSWORD, dbname=TARGET_DB)


def create_table_if_missing(conn):
    """merchants tablosunu oluştur."""
    with conn.cursor() as cur:
        cur.execute("""
                    CREATE TABLE IF NOT EXISTS merchants (
                                                             id                      SERIAL PRIMARY KEY,
                                                             merchant_code           VARCHAR(20)  NOT NULL UNIQUE,
                        legal_name              VARCHAR(150) NOT NULL,
                        trade_name              VARCHAR(150),
                        tax_number              VARCHAR(20),
                        city                    VARCHAR(50),
                        district                VARCHAR(50),
                        address                 TEXT,
                        phone                   VARCHAR(20),
                        email                   VARCHAR(100),
                        sector                  VARCHAR(50),
                        iban                    VARCHAR(34),
                        contract_date           DATE,
                        terminal_count          INTEGER      DEFAULT 1,
                        -- 15 dakikada bir değişen (cache'lenen) alanlar:
                        commission_rate         NUMERIC(5,2),
                        daily_transaction_volume NUMERIC(14,2),
                        status                  VARCHAR(20),
                        risk_score              INTEGER,
                        updated_at              TIMESTAMP    DEFAULT NOW()
                        )
                    """)
        conn.commit()
    log("✓ Tablo 'merchants' hazır.")


def seed_data(conn):
    """İlk çalıştırmada 80 kayıt üret (tablo boşsa)."""
    with conn.cursor() as cur:
        cur.execute("SELECT COUNT(*) FROM merchants")
        count = cur.fetchone()[0]
        if count > 0:
            log(f"Tabloda zaten {count} kayıt var, veri üretimi atlandı.")
            return

        log(f"{MERCHANT_COUNT} üye işyeri kaydı üretiliyor...")
        for i in range(1, MERCHANT_COUNT + 1):
            code = f"UIY{1000 + i}"
            city = random.choice(CITIES)
            sector = random.choice(SECTORS)
            cur.execute("""
                        INSERT INTO merchants (
                            merchant_code, legal_name, trade_name, tax_number,
                            city, district, address, phone, email, sector, iban,
                            contract_date, terminal_count,
                            commission_rate, daily_transaction_volume, status, risk_score
                        ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
                        """, (
                            code,
                            f"{sector} Ticaret A.S. {i}",
                            f"{sector}{i} Magaza",
                            f"{random.randint(1000000000, 9999999999)}",
                            city,
                            f"Merkez-{random.randint(1, 12)}",
                            f"{city} {random.randint(1,50)}. Sokak No:{random.randint(1,200)}",
                            f"05{random.randint(300000000, 599999999)}",
                            f"uiy{1000+i}@ornek.com",
                            sector,
                            f"TR{random.randint(10**22, 10**23 - 1)}",
                            f"20{random.randint(18,25)}-{random.randint(1,12):02d}-{random.randint(1,28):02d}",
                            random.randint(1, 8),
                            round(random.uniform(1.20, 3.50), 2),
                            round(random.uniform(5000, 250000), 2),
                            random.choices(STATUSES, weights=[70, 10, 12, 8])[0],
                            random.randint(1, 100),
                        ))
        conn.commit()
        log(f"✓ {MERCHANT_COUNT} kayıt oluşturuldu.")


def update_dynamic_fields(conn, cycle):
    """
    Rastgele seçilen kayıtların cache'lenen alanlarını değiştir.
    Değişiklikleri ekrana yazdır.
    """
    with conn.cursor() as cur:
        cur.execute("SELECT COUNT(*) FROM merchants")
        total = cur.fetchone()[0]

        # Kayıtların yaklaşık %15'ini güncelle
        update_count = max(1, int(total * 0.15))
        cur.execute(
            "SELECT id, merchant_code, commission_rate, daily_transaction_volume, "
            "status, risk_score FROM merchants ORDER BY RANDOM() LIMIT %s",
            (update_count,)
        )
        rows = cur.fetchall()

        log(f"=== Değişiklik Turu #{cycle} ===")
        changed = 0

        for (mid, code, old_comm, old_vol, old_status, old_risk) in rows:
            changes = []

            # Komisyon oranı: küçük oynama
            new_comm = old_comm
            if random.random() < 0.6:
                delta = Decimal(str(round(random.uniform(-0.35, 0.35), 2)))
                new_comm = max(Decimal("0.50"), min(Decimal("5.00"), old_comm + delta))
                if new_comm != old_comm:
                    changes.append(f"commission_rate: {old_comm}% → {new_comm}%")

            # Günlük işlem hacmi
            new_vol = old_vol
            if random.random() < 0.8:
                factor = Decimal(str(round(random.uniform(0.7, 1.4), 3)))
                new_vol = round(old_vol * factor, 2)
                changes.append(f"daily_volume: {old_vol:,.2f} → {new_vol:,.2f} TL")

            # Durum (nadiren değişir)
            new_status = old_status
            if random.random() < 0.12:
                new_status = random.choice([s for s in STATUSES if s != old_status])
                changes.append(f"status: {old_status} → {new_status}")

            # Risk skoru
            new_risk = old_risk
            if random.random() < 0.5:
                new_risk = max(1, min(100, old_risk + random.randint(-15, 15)))
                if new_risk != old_risk:
                    changes.append(f"risk_score: {old_risk} → {new_risk}")

            if not changes:
                continue

            cur.execute("""
                        UPDATE merchants
                        SET commission_rate = %s,
                            daily_transaction_volume = %s,
                            status = %s,
                            risk_score = %s,
                            updated_at = NOW()
                        WHERE id = %s
                        """, (new_comm, new_vol, new_status, new_risk, mid))

            changed += 1
            log(f"  {code} → " + " | ".join(changes))

        conn.commit()
        log(f"  Toplam {changed} kayıt güncellendi.")


def main():
    log("Üye işyeri veri üreticisi başlıyor...")
    create_database_if_missing()

    conn = get_connection()
    create_table_if_missing(conn)
    seed_data(conn)

    log(f"Güncelleme döngüsü başladı (her {UPDATE_INTERVAL_SECONDS // 60} dakikada bir).")
    log("Durdurmak için Ctrl+C\n")

    cycle = 0
    try:
        while True:
            cycle += 1
            update_dynamic_fields(conn, cycle)
            log(f"Sonraki güncelleme {UPDATE_INTERVAL_SECONDS // 60} dakika sonra...\n")
            time.sleep(UPDATE_INTERVAL_SECONDS)
    except KeyboardInterrupt:
        log("\nVeri üreticisi durduruldu.")
    finally:
        conn.close()


if __name__ == "__main__":
    main()