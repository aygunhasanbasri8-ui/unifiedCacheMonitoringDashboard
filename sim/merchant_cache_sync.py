"""
UCMD Cache Senkronizatörü (v2)
- merchant_db'den 3 tabloyu okur, Redis'e cache'ler
- Tam kayıtlar + kritik alan (hot field) key'leri + listeler
- Döngüsel: ~15 dakikada tüm kayıtları tamamlar
- TTL: 28 dakika (güvenlik ağı)
"""

import psycopg2
import psycopg2.extras
import redis
import json
import time
import math
from datetime import datetime, date
from decimal import Decimal

PG_HOST, PG_PORT = "localhost", 5432
PG_USER, PG_PASSWORD, PG_DB = "ucmd_user", "ucmd_pass", "merchant_db"
REDIS_HOST, REDIS_PORT = "localhost", 6379

TTL = 28 * 60
FULL_CYCLE_MINUTES = 15
BATCH_INTERVAL = 90          # saniye

r = redis.Redis(host=REDIS_HOST, port=REDIS_PORT, decode_responses=True)
stats = {"full": 0, "hot": 0, "lists": 0, "cycles": 0}


def log(msg):
    print(f"[{datetime.now().strftime('%H:%M:%S')}] {msg}")


def jdefault(o):
    if isinstance(o, Decimal):
        return float(o)
    if isinstance(o, (datetime, date)):
        return o.isoformat()
    return str(o)


def dumps(obj):
    return json.dumps(obj, default=jdefault, ensure_ascii=False)


def get_connection():
    return psycopg2.connect(host=PG_HOST, port=PG_PORT, user=PG_USER,
                            password=PG_PASSWORD, dbname=PG_DB,
                            cursor_factory=psycopg2.extras.RealDictCursor)


def cache_merchant(conn, m):
    """Bir merchant için tam kayıt + hot field key'leri."""
    code = m["merchant_code"]
    base = f"ucmd:merchant:{code}"

    r.setex(base, TTL, dumps(dict(m)))
    stats["full"] += 1

    # Kritik alanlar — ayrı key'ler (sık erişilen, küçük)
    hot = {
        "commission": m["commission_rate"],
        "status": m["status"],
        "risk": m["risk_score"],
        "settlement": m["settlement_period"],
        "dailyvolume": m["daily_transaction_volume"],
        "dailycount": m["daily_transaction_count"],
    }
    for field, value in hot.items():
        r.setex(f"{base}:{field}", TTL, str(value))
        stats["hot"] += 1

    # O merchant'ın terminal listesi
    with conn.cursor() as cur:
        cur.execute("""
                    SELECT terminal_code, status, daily_activity_status, daily_transaction_count
                    FROM terminals WHERE merchant_id = %s ORDER BY id
                    """, (m["id"],))
        terms = [dict(x) for x in cur.fetchall()]
    r.setex(f"{base}:terminals", TTL, dumps(terms))
    stats["full"] += 1

    # Günlük işlem özeti
    with conn.cursor() as cur:
        cur.execute("""
                    SELECT COUNT(*) AS cnt,
                           COALESCE(SUM(amount), 0) AS volume,
                           COALESCE(SUM(commission_amount), 0) AS commission,
                           COUNT(*) FILTER (WHERE status = 'APPROVED') AS approved,
                        COUNT(*) FILTER (WHERE status = 'DECLINED') AS declined
                    FROM transactions
                    WHERE merchant_id = %s AND transaction_date >= NOW() - INTERVAL '1 day'
                    """, (m["id"],))
        summary = dict(cur.fetchone())
    r.setex(f"ucmd:transaction:daily:{code}", TTL, dumps(summary))
    stats["hot"] += 1


def cache_terminal(t):
    """Terminal tam kayıt + hot field'lar."""
    code = t["terminal_code"]
    base = f"ucmd:terminal:{code}"

    r.setex(base, TTL, dumps(dict(t)))
    stats["full"] += 1

    r.setex(f"{base}:status", TTL, str(t["status"]))
    r.setex(f"{base}:daily", TTL, str(t["daily_activity_status"]))
    r.setex(f"{base}:lasttx", TTL,
            t["last_transaction_at"].isoformat() if t["last_transaction_at"] else "null")
    stats["hot"] += 3


def cache_lists(conn):
    """Toplu listeler — her turda tazelenir."""
    written = 0
    with conn.cursor() as cur:
        # Merchant: duruma göre
        cur.execute("SELECT DISTINCT status FROM merchants")
        for row in cur.fetchall():
            s = row["status"]
            c2 = conn.cursor()
            c2.execute("""SELECT merchant_code, trade_name, commission_rate, risk_score,
                                 daily_transaction_volume
                          FROM merchants WHERE status=%s ORDER BY id""", (s,))
            r.setex(f"ucmd:merchantlist:status:{s}", TTL, dumps([dict(x) for x in c2.fetchall()]))
            c2.close(); written += 1

        # Merchant: şehre göre
        cur.execute("SELECT DISTINCT city FROM merchants")
        for row in cur.fetchall():
            c = row["city"]
            c2 = conn.cursor()
            c2.execute("""SELECT merchant_code, trade_name, sector, commission_rate
                          FROM merchants WHERE city=%s ORDER BY id""", (c,))
            r.setex(f"ucmd:merchantlist:city:{c}", TTL, dumps([dict(x) for x in c2.fetchall()]))
            c2.close(); written += 1

        # Merchant: sektöre göre
        cur.execute("SELECT DISTINCT sector FROM merchants")
        for row in cur.fetchall():
            sec = row["sector"]
            c2 = conn.cursor()
            c2.execute("""SELECT merchant_code, trade_name, city, daily_transaction_volume
                          FROM merchants WHERE sector=%s ORDER BY id""", (sec,))
            r.setex(f"ucmd:merchantlist:sector:{sec}", TTL, dumps([dict(x) for x in c2.fetchall()]))
            c2.close(); written += 1

        # Yüksek riskli
        cur.execute("""SELECT merchant_code, trade_name, risk_score, status
                       FROM merchants WHERE risk_score >= 70 ORDER BY risk_score DESC""")
        r.setex("ucmd:merchantlist:highrisk", TTL, dumps([dict(x) for x in cur.fetchall()]))
        written += 1

        # Terminal: duruma göre (OFFLINE kritik!)
        cur.execute("SELECT DISTINCT status FROM terminals")
        for row in cur.fetchall():
            s = row["status"]
            c2 = conn.cursor()
            c2.execute("""SELECT t.terminal_code, t.model, t.daily_activity_status,
                                 m.merchant_code
                          FROM terminals t JOIN merchants m ON m.id=t.merchant_id
                          WHERE t.status=%s ORDER BY t.id""", (s,))
            r.setex(f"ucmd:terminallist:status:{s}", TTL, dumps([dict(x) for x in c2.fetchall()]))
            c2.close(); written += 1

        # Terminal: günlük aktifliğe göre
        cur.execute("SELECT DISTINCT daily_activity_status FROM terminals")
        for row in cur.fetchall():
            d = row["daily_activity_status"]
            c2 = conn.cursor()
            c2.execute("""SELECT t.terminal_code, t.daily_transaction_count, m.merchant_code
                          FROM terminals t JOIN merchants m ON m.id=t.merchant_id
                          WHERE t.daily_activity_status=%s ORDER BY t.id""", (d,))
            r.setex(f"ucmd:terminallist:daily:{d}", TTL, dumps([dict(x) for x in c2.fetchall()]))
            c2.close(); written += 1

        # Sektör istatistikleri
        cur.execute("""
                    SELECT sector, COUNT(*) AS merchant_count,
                           AVG(commission_rate) AS avg_commission,
                           SUM(daily_transaction_volume) AS total_volume
                    FROM merchants GROUP BY sector
                    """)
        for row in cur.fetchall():
            r.setex(f"ucmd:stats:sector:{row['sector']}", TTL, dumps(dict(row)))
            written += 1

        # Global günlük özet
        cur.execute("""
                    SELECT COUNT(*) AS tx_count,
                           COALESCE(SUM(amount),0) AS volume,
                           COALESCE(SUM(commission_amount),0) AS commission,
                           COUNT(*) FILTER (WHERE status='APPROVED') AS approved,
                        COUNT(*) FILTER (WHERE status='DECLINED') AS declined
                    FROM transactions WHERE transaction_date >= NOW() - INTERVAL '1 day'
                    """)
        r.setex("ucmd:stats:daily:global", TTL, dumps(dict(cur.fetchone())))
        written += 1

    stats["lists"] += written
    return written


def main():
    log("Cache senkronizatörü (v2) başlıyor...")
    log(f"Redis: {r.ping()}")

    conn = get_connection()

    with conn.cursor() as cur:
        cur.execute("SELECT COUNT(*) AS c FROM merchants")
        m_total = cur.fetchone()["c"]
        cur.execute("SELECT COUNT(*) AS c FROM terminals")
        t_total = cur.fetchone()["c"]

    turns = max(1, (FULL_CYCLE_MINUTES * 60) // BATCH_INTERVAL)
    m_batch = math.ceil(m_total / turns)
    t_batch = math.ceil(t_total / turns)

    log(f"{m_total} merchant, {t_total} terminal | {turns} tur | "
        f"tur başına {m_batch} merchant + {t_batch} terminal")
    log(f"TTL: {TTL//60} dk | Tur aralığı: {BATCH_INTERVAL} sn\n")

    m_offset = t_offset = 0
    turn = 0

    try:
        while True:
            turn += 1
            with conn.cursor() as cur:
                cur.execute("SELECT COUNT(*) AS c FROM merchants")
                m_total = cur.fetchone()["c"]
                cur.execute("SELECT COUNT(*) AS c FROM terminals")
                t_total = cur.fetchone()["c"]

            if m_offset >= m_total:
                m_offset = 0
                stats["cycles"] += 1
                log(f"↻ Tam döngü #{stats['cycles']} tamamlandı\n")
            if t_offset >= t_total:
                t_offset = 0

            log(f"--- Tur {turn} ---")

            # Merchants
            with conn.cursor() as cur:
                cur.execute("SELECT * FROM merchants ORDER BY id OFFSET %s LIMIT %s",
                            (m_offset, m_batch))
                merchants = cur.fetchall()
            for m in merchants:
                cache_merchant(conn, m)
            log(f"  ✓ {len(merchants)} merchant "
                f"(#{m_offset+1}-{m_offset+len(merchants)}/{m_total}) + hot field'lar")
            m_offset += len(merchants)

            # Terminals
            with conn.cursor() as cur:
                cur.execute("SELECT * FROM terminals ORDER BY id OFFSET %s LIMIT %s",
                            (t_offset, t_batch))
                terminals = cur.fetchall()
            for t in terminals:
                cache_terminal(t)
            log(f"  ✓ {len(terminals)} terminal "
                f"(#{t_offset+1}-{t_offset+len(terminals)}/{t_total}) + hot field'lar")
            t_offset += len(terminals)

            # Listeler
            n = cache_lists(conn)
            log(f"  ✓ {n} liste/istatistik güncellendi")

            log(f"  Toplam yazma: {stats['full']} tam, {stats['hot']} hot, "
                f"{stats['lists']} liste | Redis key: {r.dbsize()}")
            log(f"  Sonraki tur {BATCH_INTERVAL} sn sonra...\n")

            time.sleep(BATCH_INTERVAL)

    except KeyboardInterrupt:
        log("\nSenkronizatör durduruldu.")
        log(f"Özet: {stats['full']} tam kayıt, {stats['hot']} hot field, "
            f"{stats['lists']} liste, {stats['cycles']} tam döngü")
    finally:
        conn.close()


if __name__ == "__main__":
    main()