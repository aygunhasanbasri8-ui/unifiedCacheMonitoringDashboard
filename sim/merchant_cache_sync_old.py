"""
UCMD Üye İşyeri Cache Senkronizatörü
- merchant_db'den kayıtları okur, JSON olarak Redis'e yazar
- Her turda kayıtların bir kısmını işler (döngüsel), ~15 dakikada tümünü tamamlar
- Tekli kayıtlar ve listeler (eşit dağılım)
- TTL: 28 dakika (bir tur kaçarsa hâlâ geçerli, iki tur kaçarsa expire)

"""

import psycopg2
import psycopg2.extras
import redis
import json
import time
import math
from datetime import datetime, date
from decimal import Decimal

# --- Ayarlar ---
PG_HOST, PG_PORT = "localhost", 5432
PG_USER, PG_PASSWORD, PG_DB = "ucmd_user", "ucmd_pass", "merchant_db"

REDIS_HOST, REDIS_PORT = "localhost", 6379

PREFIX = "ucmd:merchant"
LIST_PREFIX = "ucmd:merchantlist"

TTL_SECONDS = 28 * 60          # 28 dakika
FULL_CYCLE_MINUTES = 15        # tüm kayıtlar bu sürede tamamlanmalı
BATCH_INTERVAL_SECONDS = 90    # her tur arası 1.5 dakika

r = redis.Redis(host=REDIS_HOST, port=REDIS_PORT, decode_responses=True)

stats = {"single_writes": 0, "list_writes": 0, "cycles": 0}


def log(msg):
    print(f"[{datetime.now().strftime('%H:%M:%S')}] {msg}")


def json_default(obj):
    """Decimal ve date/datetime'ı JSON'a çevir."""
    if isinstance(obj, Decimal):
        return float(obj)
    if isinstance(obj, (datetime, date)):
        return obj.isoformat()
    return str(obj)


def get_connection():
    return psycopg2.connect(host=PG_HOST, port=PG_PORT, user=PG_USER,
                            password=PG_PASSWORD, dbname=PG_DB,
                            cursor_factory=psycopg2.extras.RealDictCursor)


def fetch_batch(conn, offset, limit):
    """Belirli aralıktaki kayıtları çek."""
    with conn.cursor() as cur:
        cur.execute(
            "SELECT * FROM merchants ORDER BY id OFFSET %s LIMIT %s",
            (offset, limit)
        )
        return cur.fetchall()


def total_count(conn):
    with conn.cursor() as cur:
        cur.execute("SELECT COUNT(*) AS c FROM merchants")
        return cur.fetchone()["c"]


def cache_single(record):
    """Tek bir üye işyeri kaydını Redis'e yaz."""
    key = f"{PREFIX}:{record['merchant_code']}"
    r.setex(key, TTL_SECONDS, json.dumps(dict(record), default=json_default, ensure_ascii=False))
    stats["single_writes"] += 1
    return key


def cache_lists(conn):
    """
    Gruplu listeleri Redis'e yaz:
      - duruma göre (ucmd:merchantlist:status:ACTIVE)
      - şehre göre (ucmd:merchantlist:city:Istanbul)
      - sektöre göre (ucmd:merchantlist:sector:Market)
      - yüksek riskli (ucmd:merchantlist:highrisk)
    """
    written = []
    with conn.cursor() as cur:
        # Duruma göre
        cur.execute("SELECT DISTINCT status FROM merchants")
        for row in cur.fetchall():
            status = row["status"]
            cur2 = conn.cursor()
            cur2.execute(
                "SELECT merchant_code, trade_name, commission_rate, "
                "daily_transaction_volume, risk_score "
                "FROM merchants WHERE status = %s ORDER BY id", (status,))
            data = [dict(x) for x in cur2.fetchall()]
            cur2.close()
            key = f"{LIST_PREFIX}:status:{status}"
            r.setex(key, TTL_SECONDS, json.dumps(data, default=json_default, ensure_ascii=False))
            written.append((key, len(data)))
            stats["list_writes"] += 1

        # Şehre göre
        cur.execute("SELECT DISTINCT city FROM merchants")
        for row in cur.fetchall():
            city = row["city"]
            cur2 = conn.cursor()
            cur2.execute(
                "SELECT merchant_code, trade_name, sector, commission_rate "
                "FROM merchants WHERE city = %s ORDER BY id", (city,))
            data = [dict(x) for x in cur2.fetchall()]
            cur2.close()
            key = f"{LIST_PREFIX}:city:{city}"
            r.setex(key, TTL_SECONDS, json.dumps(data, default=json_default, ensure_ascii=False))
            written.append((key, len(data)))
            stats["list_writes"] += 1

        # Sektöre göre
        cur.execute("SELECT DISTINCT sector FROM merchants")
        for row in cur.fetchall():
            sector = row["sector"]
            cur2 = conn.cursor()
            cur2.execute(
                "SELECT merchant_code, trade_name, city, daily_transaction_volume "
                "FROM merchants WHERE sector = %s ORDER BY id", (sector,))
            data = [dict(x) for x in cur2.fetchall()]
            cur2.close()
            key = f"{LIST_PREFIX}:sector:{sector}"
            r.setex(key, TTL_SECONDS, json.dumps(data, default=json_default, ensure_ascii=False))
            written.append((key, len(data)))
            stats["list_writes"] += 1

        # Yüksek riskli
        cur.execute(
            "SELECT merchant_code, trade_name, risk_score, status "
            "FROM merchants WHERE risk_score >= 70 ORDER BY risk_score DESC")
        data = [dict(x) for x in cur.fetchall()]
        key = f"{LIST_PREFIX}:highrisk"
        r.setex(key, TTL_SECONDS, json.dumps(data, default=json_default, ensure_ascii=False))
        written.append((key, len(data)))
        stats["list_writes"] += 1

    return written


def main():
    log("Cache senkronizatörü başlıyor...")
    log(f"Redis bağlantısı: {r.ping()}")

    conn = get_connection()
    total = total_count(conn)

    # Kaç turda tamamlanacak? (15 dk / 1.5 dk = 10 tur)
    turns = max(1, (FULL_CYCLE_MINUTES * 60) // BATCH_INTERVAL_SECONDS)
    batch_size = math.ceil(total / turns)

    log(f"Toplam {total} kayıt | {turns} turda tamamlanacak | tur başına ~{batch_size} kayıt")
    log(f"TTL: {TTL_SECONDS // 60} dakika | Tur aralığı: {BATCH_INTERVAL_SECONDS} sn")
    log("Durdurmak için Ctrl+C\n")

    offset = 0
    turn = 0

    try:
        while True:
            turn += 1
            current_total = total_count(conn)

            # Tur başı: offset sınırı aşarsa başa dön (yeni döngü)
            if offset >= current_total:
                offset = 0
                stats["cycles"] += 1
                log(f"↻ Tam döngü tamamlandı (#{stats['cycles']}) — baştan başlanıyor\n")

            batch = fetch_batch(conn, offset, batch_size)
            log(f"--- Tur {turn} | kayıt {offset + 1}-{offset + len(batch)} / {current_total} ---")

            for record in batch:
                key = cache_single(record)
                log(f"  ✓ {key}  (komisyon: {record['commission_rate']}%, "
                    f"durum: {record['status']}, risk: {record['risk_score']})")

            offset += len(batch)

            # Listeler her turda tazelenir (değişen veriyi hızlı yansıtsın)
            list_results = cache_lists(conn)
            log(f"  ↳ {len(list_results)} liste güncellendi "
                f"(örn: {list_results[0][0]} → {list_results[0][1]} kayıt)")

            log(f"  Toplam: {stats['single_writes']} tekli, {stats['list_writes']} liste yazıldı | "
                f"Redis key sayısı: {r.dbsize()}")
            log(f"  Sonraki tur {BATCH_INTERVAL_SECONDS} sn sonra...\n")

            time.sleep(BATCH_INTERVAL_SECONDS)

    except KeyboardInterrupt:
        log("\nSenkronizatör durduruldu.")
        log(f"Özet: {stats['single_writes']} tekli yazma, {stats['list_writes']} liste yazma, "
            f"{stats['cycles']} tam döngü")
    finally:
        conn.close()


if __name__ == "__main__":
    main()