"""
UCMD Üye İşyeri Veri Üreticisi (v2)
- merchant_db veritabanı + 3 ilişkili tablo oluşturur
- merchants (100) → terminals (~250) → transactions (~2000)
- Her 15 dakikada bir dinamik alanları günceller
- Değişiklikleri log ekranında gösterir
"""

import psycopg2
from psycopg2 import sql
import random
import time
from datetime import datetime, timedelta, date
from decimal import Decimal

# --- Bağlantı ---
PG_HOST, PG_PORT = "localhost", 5432
PG_USER, PG_PASSWORD = "ucmd_user", "ucmd_pass"
ADMIN_DB, TARGET_DB = "ucmd_db", "merchant_db"

MERCHANT_COUNT = 100
UPDATE_INTERVAL_SECONDS = 15 * 60

CITIES = ["Istanbul", "Ankara", "Izmir", "Bursa", "Antalya", "Adana", "Konya", "Gaziantep"]
DISTRICTS = ["Merkez", "Kadikoy", "Cankaya", "Bornova", "Nilufer", "Muratpasa", "Seyhan"]
SECTORS = ["Market", "Restoran", "Giyim", "Elektronik", "Akaryakit", "Eczane", "Kuyumcu", "Turizm"]
MCC_CODES = {"Market": "5411", "Restoran": "5812", "Giyim": "5651", "Elektronik": "5732",
             "Akaryakit": "5541", "Eczane": "5912", "Kuyumcu": "5944", "Turizm": "4722"}
BANKS = ["Ziraat", "Is Bankasi", "Garanti BBVA", "Yapi Kredi", "Akbank", "QNB", "Vakifbank"]
CONTRACT_STATUSES = ["ACTIVE", "SUSPENDED", "TERMINATED", "PENDING"]
STATUSES = ["ACTIVE", "SUSPENDED", "PENDING", "PASSIVE"]
SETTLEMENT_PERIODS = ["T+1", "T+2", "T+7"]

TERMINAL_MODELS = ["Ingenico Move5000", "Ingenico Desk3500", "Verifone V240m",
                   "Verifone P400", "PAX A920", "PAX S920"]
TERMINAL_TYPES = ["POS", "VIRTUAL_POS", "MOBILE"]
TERMINAL_STATUSES = ["ACTIVE", "OFFLINE", "MAINTENANCE"]
DAILY_ACTIVITY = ["ACTIVE", "IDLE", "OFFLINE"]

TX_TYPES = ["SALE", "REFUND", "VOID", "INSTALLMENT"]
CARD_TYPES = ["CREDIT", "DEBIT", "PREPAID"]
CARD_BRANDS = ["VISA", "MASTERCARD", "TROY", "AMEX"]
TX_STATUSES = ["APPROVED", "DECLINED", "PENDING", "REVERSED"]
RESPONSE_CODES = {"APPROVED": "00", "DECLINED": "51", "PENDING": "09", "REVERSED": "17"}
CURRENCIES = ["TRY", "TRY", "TRY", "USD", "EUR"]   # ağırlıklı TRY


def log(msg):
    print(f"[{datetime.now().strftime('%H:%M:%S')}] {msg}")


def create_database_if_missing():
    conn = psycopg2.connect(host=PG_HOST, port=PG_PORT, user=PG_USER,
                            password=PG_PASSWORD, dbname=ADMIN_DB)
    conn.autocommit = True
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


def create_tables(conn):
    with conn.cursor() as cur:
        cur.execute("""
                    CREATE TABLE IF NOT EXISTS merchants (
                                                             id                       BIGSERIAL PRIMARY KEY,
                                                             merchant_code            VARCHAR(20)  NOT NULL UNIQUE,
                        tax_number               VARCHAR(11)  NOT NULL UNIQUE,
                        legal_name               VARCHAR(255) NOT NULL,
                        trade_name               VARCHAR(255),
                        mcc_code                 VARCHAR(4),
                        sector                   VARCHAR(50),
                        city                     VARCHAR(50),
                        district                 VARCHAR(50),
                        address                  TEXT,
                        contact_person           VARCHAR(150),
                        phone                    VARCHAR(20),
                        email                    VARCHAR(100),
                        iban                     VARCHAR(26),
                        bank_name                VARCHAR(100),
                        contract_date            DATE,
                        contract_status          VARCHAR(20),
                        commission_rate          NUMERIC(5,2),
                        daily_transaction_volume NUMERIC(19,2),
                        daily_transaction_count  INTEGER      DEFAULT 0,
                        status                   VARCHAR(20),
                        risk_score               INTEGER,
                        settlement_period        VARCHAR(10),
                        created_at               TIMESTAMP    NOT NULL DEFAULT NOW(),
                        updated_at               TIMESTAMP    NOT NULL DEFAULT NOW()
                        )
                    """)

        cur.execute("""
                    CREATE TABLE IF NOT EXISTS terminals (
                                                             id                      BIGSERIAL PRIMARY KEY,
                                                             terminal_code           VARCHAR(20)  NOT NULL UNIQUE,
                        merchant_id             BIGINT       NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
                        serial_number           VARCHAR(30)  NOT NULL UNIQUE,
                        model                   VARCHAR(50),
                        terminal_type           VARCHAR(20),
                        installation_date       DATE,
                        status                  VARCHAR(20),
                        daily_activity_status   VARCHAR(20),
                        last_transaction_at     TIMESTAMP,
                        daily_transaction_count INTEGER      DEFAULT 0,
                        software_version        VARCHAR(20),
                        created_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
                        updated_at              TIMESTAMP    NOT NULL DEFAULT NOW()
                        )
                    """)

        cur.execute("""
                    CREATE TABLE IF NOT EXISTS transactions (
                                                                id                 BIGSERIAL PRIMARY KEY,
                                                                transaction_ref    VARCHAR(32)  NOT NULL UNIQUE,
                        terminal_id        BIGINT       NOT NULL REFERENCES terminals(id) ON DELETE CASCADE,
                        merchant_id        BIGINT       NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
                        amount             NUMERIC(19,2) NOT NULL,
                        commission_amount  NUMERIC(19,2),
                        net_amount         NUMERIC(19,2),
                        currency           VARCHAR(3),
                        transaction_type   VARCHAR(20),
                        installment_count  INTEGER      DEFAULT 1,
                        card_type          VARCHAR(20),
                        card_brand         VARCHAR(20),
                        masked_card_no     VARCHAR(20),
                        status             VARCHAR(20),
                        response_code      VARCHAR(4),
                        transaction_date   TIMESTAMP    NOT NULL,
                        created_at         TIMESTAMP    NOT NULL DEFAULT NOW()
                        )
                    """)

        cur.execute("CREATE INDEX IF NOT EXISTS idx_tx_merchant ON transactions(merchant_id)")
        cur.execute("CREATE INDEX IF NOT EXISTS idx_tx_terminal ON transactions(terminal_id)")
        cur.execute("CREATE INDEX IF NOT EXISTS idx_term_merchant ON terminals(merchant_id)")
        conn.commit()
    log("✓ Tablolar hazır: merchants, terminals, transactions")


def random_iban():
    return "TR" + "".join(str(random.randint(0, 9)) for _ in range(24))


def random_tax_number():
    return "".join(str(random.randint(0, 9)) for _ in range(10)).zfill(11)


def seed_merchants(conn):
    with conn.cursor() as cur:
        cur.execute("SELECT COUNT(*) FROM merchants")
        if cur.fetchone()[0] > 0:
            log("Merchants tablosunda kayıt var, üretim atlandı.")
            return

        log(f"{MERCHANT_COUNT} üye işyeri üretiliyor...")
        used_tax = set()
        for i in range(1, MERCHANT_COUNT + 1):
            sector = random.choice(SECTORS)
            city = random.choice(CITIES)

            tax = random_tax_number()
            while tax in used_tax:
                tax = random_tax_number()
            used_tax.add(tax)

            cur.execute("""
                        INSERT INTO merchants (
                            merchant_code, tax_number, legal_name, trade_name, mcc_code,
                            sector, city, district, address, contact_person, phone, email,
                            iban, bank_name, contract_date, contract_status,
                            commission_rate, daily_transaction_volume, daily_transaction_count,
                            status, risk_score, settlement_period
                        ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
                        """, (
                            f"UIY{1000 + i}",
                            tax,
                            f"{sector} Ticaret Limited Sirketi {i}",
                            f"{sector} Magaza {i}",
                            MCC_CODES[sector],
                            sector, city, random.choice(DISTRICTS),
                            f"{city} {random.randint(1,60)}. Sokak No:{random.randint(1,250)}",
                            f"Yetkili Kisi {i}",
                            f"05{random.randint(300000000, 599999999)}",
                            f"uiy{1000+i}@ornek.com.tr",
                            random_iban(),
                            random.choice(BANKS),
                            date(random.randint(2018, 2025), random.randint(1, 12), random.randint(1, 28)),
                            random.choices(CONTRACT_STATUSES, weights=[75, 10, 5, 10])[0],
                            round(random.uniform(1.20, 3.80), 2),
                            round(random.uniform(5000, 500000), 2),
                            random.randint(10, 800),
                            random.choices(STATUSES, weights=[70, 10, 12, 8])[0],
                            random.randint(1, 100),
                            random.choices(SETTLEMENT_PERIODS, weights=[50, 35, 15])[0],
                        ))
        conn.commit()
        log(f"✓ {MERCHANT_COUNT} üye işyeri oluşturuldu.")


def seed_terminals(conn):
    with conn.cursor() as cur:
        cur.execute("SELECT COUNT(*) FROM terminals")
        if cur.fetchone()[0] > 0:
            log("Terminals tablosunda kayıt var, üretim atlandı.")
            return

        cur.execute("SELECT id, merchant_code FROM merchants ORDER BY id")
        merchants = cur.fetchall()

        log("Terminaller üretiliyor...")
        counter = 50000
        total = 0
        for (mid, mcode) in merchants:
            terminal_count = random.choices([1, 2, 3, 4, 5], weights=[40, 30, 15, 10, 5])[0]
            for _ in range(terminal_count):
                counter += 1
                status = random.choices(TERMINAL_STATUSES, weights=[80, 12, 8])[0]
                # daily_activity: status OFFLINE ise zorunlu OFFLINE
                if status == "OFFLINE":
                    daily = "OFFLINE"
                else:
                    daily = random.choices(["ACTIVE", "IDLE"], weights=[75, 25])[0]

                cur.execute("""
                            INSERT INTO terminals (
                                terminal_code, merchant_id, serial_number, model, terminal_type,
                                installation_date, status, daily_activity_status,
                                last_transaction_at, daily_transaction_count, software_version
                            ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
                            """, (
                                f"TRM{counter}",
                                mid,
                                f"SN{random.randint(10**9, 10**10 - 1)}",
                                random.choice(TERMINAL_MODELS),
                                random.choices(TERMINAL_TYPES, weights=[70, 20, 10])[0],
                                date(random.randint(2019, 2025), random.randint(1, 12), random.randint(1, 28)),
                                status,
                                daily,
                                datetime.now() - timedelta(minutes=random.randint(1, 2880)),
                                random.randint(0, 250) if daily == "ACTIVE" else 0,
                                f"v{random.randint(2,5)}.{random.randint(0,9)}.{random.randint(0,20)}",
                            ))
                total += 1
        conn.commit()
        log(f"✓ {total} terminal oluşturuldu.")


def seed_transactions(conn):
    with conn.cursor() as cur:
        cur.execute("SELECT COUNT(*) FROM transactions")
        if cur.fetchone()[0] > 0:
            log("Transactions tablosunda kayıt var, üretim atlandı.")
            return

        cur.execute("""
                    SELECT t.id, t.merchant_id, m.commission_rate
                    FROM terminals t JOIN merchants m ON m.id = t.merchant_id
                    WHERE t.status != 'OFFLINE'
                    """)
        terminals = cur.fetchall()

        log("İşlemler üretiliyor...")
        total = 0
        for (tid, mid, commission) in terminals:
            tx_count = random.randint(3, 15)
            for _ in range(tx_count):
                amount = Decimal(str(round(random.uniform(15, 8500), 2)))
                comm = (amount * commission / Decimal("100")).quantize(Decimal("0.01"))
                status = random.choices(TX_STATUSES, weights=[85, 8, 4, 3])[0]
                tx_type = random.choices(TX_TYPES, weights=[70, 10, 5, 15])[0]

                cur.execute("""
                            INSERT INTO transactions (
                                transaction_ref, terminal_id, merchant_id, amount,
                                commission_amount, net_amount, currency, transaction_type,
                                installment_count, card_type, card_brand, masked_card_no,
                                status, response_code, transaction_date
                            ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
                            """, (
                                f"TX{random.randint(10**15, 10**16 - 1)}",
                                tid, mid, amount, comm, amount - comm,
                                random.choice(CURRENCIES),
                                tx_type,
                                random.choice([1, 1, 1, 2, 3, 6, 9, 12]) if tx_type == "INSTALLMENT" else 1,
                                random.choices(CARD_TYPES, weights=[60, 35, 5])[0],
                                random.choices(CARD_BRANDS, weights=[45, 40, 12, 3])[0],
                                f"{random.randint(4000,5999)}****{random.randint(1000,9999)}",
                                status,
                                RESPONSE_CODES[status],
                                datetime.now() - timedelta(minutes=random.randint(1, 1440)),
                            ))
                total += 1
        conn.commit()
        log(f"✓ {total} işlem oluşturuldu.")


def update_dynamic_fields(conn, cycle):
    """Dinamik alanları güncelle ve değişiklikleri logla."""
    log(f"=== Değişiklik Turu #{cycle} ===")

    with conn.cursor() as cur:
        # --- MERCHANTS ---
        cur.execute("SELECT COUNT(*) FROM merchants")
        total_m = cur.fetchone()[0]
        cur.execute("""
                    SELECT id, merchant_code, commission_rate, daily_transaction_volume,
                           daily_transaction_count, status, risk_score, settlement_period
                    FROM merchants ORDER BY RANDOM() LIMIT %s
                    """, (max(1, int(total_m * 0.15)),))
        merchants = cur.fetchall()

        m_changed = 0
        for (mid, code, comm, vol, cnt, status, risk, settle) in merchants:
            changes = []
            new_comm, new_vol, new_cnt = comm, vol, cnt
            new_status, new_risk, new_settle = status, risk, settle

            if random.random() < 0.6:
                delta = Decimal(str(round(random.uniform(-0.30, 0.30), 2)))
                new_comm = max(Decimal("0.50"), min(Decimal("5.00"), comm + delta))
                if new_comm != comm:
                    changes.append(f"commission: {comm}% → {new_comm}%")

            if random.random() < 0.85:
                new_vol = round(vol * Decimal(str(round(random.uniform(0.75, 1.35), 3))), 2)
                changes.append(f"volume: {vol:,.2f} → {new_vol:,.2f} TL")

            if random.random() < 0.8:
                new_cnt = max(0, cnt + random.randint(-60, 90))
                changes.append(f"tx_count: {cnt} → {new_cnt}")

            if random.random() < 0.10:
                new_status = random.choice([s for s in STATUSES if s != status])
                changes.append(f"status: {status} → {new_status}")

            if random.random() < 0.5:
                new_risk = max(1, min(100, risk + random.randint(-12, 12)))
                if new_risk != risk:
                    changes.append(f"risk: {risk} → {new_risk}")

            if random.random() < 0.05:
                new_settle = random.choice([s for s in SETTLEMENT_PERIODS if s != settle])
                changes.append(f"settlement: {settle} → {new_settle}")

            if not changes:
                continue

            cur.execute("""
                        UPDATE merchants SET commission_rate=%s, daily_transaction_volume=%s,
                                             daily_transaction_count=%s, status=%s, risk_score=%s,
                                             settlement_period=%s, updated_at=NOW()
                        WHERE id=%s
                        """, (new_comm, new_vol, new_cnt, new_status, new_risk, new_settle, mid))
            m_changed += 1
            log(f"  [M] {code} → " + " | ".join(changes))

        # --- TERMINALS ---
        cur.execute("SELECT COUNT(*) FROM terminals")
        total_t = cur.fetchone()[0]
        cur.execute("""
                    SELECT id, terminal_code, status, daily_activity_status, daily_transaction_count
                    FROM terminals ORDER BY RANDOM() LIMIT %s
                    """, (max(1, int(total_t * 0.20)),))
        terminals = cur.fetchall()

        t_changed = 0
        for (tid, tcode, status, daily, cnt) in terminals:
            changes = []
            new_status, new_daily, new_cnt = status, daily, cnt

            if random.random() < 0.12:
                new_status = random.choices(TERMINAL_STATUSES, weights=[80, 12, 8])[0]
                if new_status != status:
                    changes.append(f"status: {status} → {new_status}")

            # daily_activity mantığı: OFFLINE terminal işlem yapamaz
            if new_status == "OFFLINE":
                new_daily = "OFFLINE"
                new_cnt = 0
            else:
                if random.random() < 0.4:
                    new_daily = random.choices(["ACTIVE", "IDLE"], weights=[75, 25])[0]
                if new_daily == "ACTIVE":
                    new_cnt = max(0, cnt + random.randint(0, 40))
                else:
                    new_cnt = 0

            if new_daily != daily:
                changes.append(f"daily_activity: {daily} → {new_daily}")
            if new_cnt != cnt:
                changes.append(f"tx_count: {cnt} → {new_cnt}")

            if not changes:
                continue

            cur.execute("""
                        UPDATE terminals SET status=%s, daily_activity_status=%s,
                                             daily_transaction_count=%s,
                                             last_transaction_at = CASE WHEN %s='ACTIVE' THEN NOW() ELSE last_transaction_at END,
                                             updated_at=NOW()
                        WHERE id=%s
                        """, (new_status, new_daily, new_cnt, new_daily, tid))
            t_changed += 1
            log(f"  [T] {tcode} → " + " | ".join(changes))

        # --- YENİ İŞLEMLER ---
        cur.execute("""
                    SELECT t.id, t.merchant_id, m.commission_rate
                    FROM terminals t JOIN merchants m ON m.id = t.merchant_id
                    WHERE t.daily_activity_status = 'ACTIVE'
                    ORDER BY RANDOM() LIMIT 30
                    """)
        active_terminals = cur.fetchall()

        tx_added = 0
        for (tid, mid, commission) in active_terminals:
            for _ in range(random.randint(1, 4)):
                amount = Decimal(str(round(random.uniform(15, 8500), 2)))
                comm = (amount * commission / Decimal("100")).quantize(Decimal("0.01"))
                status = random.choices(TX_STATUSES, weights=[85, 8, 4, 3])[0]
                tx_type = random.choices(TX_TYPES, weights=[70, 10, 5, 15])[0]
                cur.execute("""
                            INSERT INTO transactions (
                                transaction_ref, terminal_id, merchant_id, amount,
                                commission_amount, net_amount, currency, transaction_type,
                                installment_count, card_type, card_brand, masked_card_no,
                                status, response_code, transaction_date
                            ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,NOW())
                            """, (
                                f"TX{random.randint(10**15, 10**16 - 1)}",
                                tid, mid, amount, comm, amount - comm,
                                random.choice(CURRENCIES), tx_type,
                                random.choice([1, 2, 3, 6, 12]) if tx_type == "INSTALLMENT" else 1,
                                random.choices(CARD_TYPES, weights=[60, 35, 5])[0],
                                random.choices(CARD_BRANDS, weights=[45, 40, 12, 3])[0],
                                f"{random.randint(4000,5999)}****{random.randint(1000,9999)}",
                                status, RESPONSE_CODES[status],
                            ))
                tx_added += 1

        conn.commit()
        log(f"  Özet: {m_changed} merchant, {t_changed} terminal güncellendi, "
            f"{tx_added} yeni işlem eklendi.")


def main():
    log("Üye işyeri veri üreticisi (v2) başlıyor...")
    create_database_if_missing()

    conn = get_connection()
    create_tables(conn)
    seed_merchants(conn)
    seed_terminals(conn)
    seed_transactions(conn)

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