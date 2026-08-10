"""
UCMD Cache Trafik Simülatörü
"""

import redis
import random
import time
import json
from datetime import datetime

# --- Bağlantı ---
r = redis.Redis(host='localhost', port=6380, decode_responses=True)

# --- Sabitler ---
PREFIX = "ucmd"
USER_IDS = list(range(1001, 1051))      # 50 kullanıcı
PRODUCT_IDS = list(range(5001, 5031))   # 30 ürün

USER_LIST_KEY = f"{PREFIX}:userlist"
PRODUCT_LIST_KEY = f"{PREFIX}:productlist"

stats = {"hits": 0, "misses": 0, "writes": 0, "invalidations": 0}


def log(msg):
    print(f"[{datetime.now().strftime('%H:%M:%S')}] {msg}")


def fake_user(user_id):
    """DB'den çekilmiş gibi sahte kullanıcı kaydı."""
    return json.dumps({
        "id": user_id,
        "name": f"user{user_id}",
        "email": f"user{user_id}@ucmd.local",
        "active": True
    })


def get_user(user_id):
    """
    Cache-aside: önce cache'e bak, yoksa 'DB'den çek' ve cache'e yaz.
    """
    key = f"{PREFIX}:user:{user_id}"
    cached = r.get(key)
    if cached:
        stats["hits"] += 1
        return cached
    stats["misses"] += 1
    value = fake_user(user_id)
    r.setex(key, 300, value)   # 5 dk TTL
    stats["writes"] += 1
    return value


def get_user_list():
    """
    Liste cache'i: varsa cache'ten dön (DB'ye gitme), yoksa oluştur.
    """
    cached = r.get(USER_LIST_KEY)
    if cached:
        stats["hits"] += 1
        log("  getUserList → CACHE HIT (DB'ye gidilmedi)")
        return cached
    stats["misses"] += 1
    log("  getUserList → CACHE MISS (DB'den çekiliyor, cache'e yazılıyor)")
    user_list = json.dumps([{"id": uid, "name": f"user{uid}"} for uid in USER_IDS])
    r.setex(USER_LIST_KEY, 600, user_list)   # 10 dk TTL
    stats["writes"] += 1
    return user_list


def invalidate_user_list():
    """
    Bir kullanıcı eklenince/silinince liste cache'i geçersiz kılınır.
    Yoksa eski liste dönmeye devam eder.
    """
    deleted = r.delete(USER_LIST_KEY)
    if deleted:
        stats["invalidations"] += 1
        log("  ⚠ INVALIDATION → ucmd:userlist silindi (veri değişti)")


def add_user():
    """Yeni kullanıcı ekle + liste cache'ini invalidate et."""
    new_id = max(USER_IDS) + 1
    USER_IDS.append(new_id)
    r.setex(f"{PREFIX}:user:{new_id}", 300, fake_user(new_id))
    stats["writes"] += 1
    log(f"  + Kullanıcı eklendi: ucmd:user:{new_id}")
    invalidate_user_list()


def delete_user():
    """Rastgele bir kullanıcı sil + liste cache'ini invalidate et."""
    if len(USER_IDS) <= 10:
        return
    uid = random.choice(USER_IDS)
    USER_IDS.remove(uid)
    r.delete(f"{PREFIX}:user:{uid}")
    log(f"  - Kullanıcı silindi: ucmd:user:{uid}")
    invalidate_user_list()


def create_session():
    """Kısa TTL'li session key (expire olup düşecek)."""
    sid = f"{random.randint(100000, 999999)}"
    r.setex(f"{PREFIX}:session:{sid}", random.randint(30, 90), "active")
    stats["writes"] += 1


def get_product(pid):
    """Ürün okuma (cache-aside)."""
    key = f"{PREFIX}:product:{pid}"
    if r.get(key):
        stats["hits"] += 1
    else:
        stats["misses"] += 1
        r.setex(key, 240, json.dumps({"id": pid, "name": f"product{pid}"}))
        stats["writes"] += 1


def miss_traffic():
    """Var olmayan key okuma → kasıtlı MISS (hit ratio'yu gerçekçi tut)."""
    ghost_id = random.randint(90000, 99999)
    r.get(f"{PREFIX}:user:{ghost_id}")
    stats["misses"] += 1


def print_stats():
    total = stats["hits"] + stats["misses"]
    ratio = (stats["hits"] / total * 100) if total else 0
    log(f"📊 Hit: {stats['hits']} | Miss: {stats['misses']} | "
        f"Hit Ratio: {ratio:.1f}% | Yazma: {stats['writes']} | "
        f"Invalidation: {stats['invalidations']} | "
        f"Toplam Key: {r.dbsize()}")


def main():
    log("Simülatör başladı. Durdurmak için Ctrl+C")
    log(f"Redis bağlantısı: {r.ping()}")

    cycle = 0
    try:
        while True:
            cycle += 1
            log(f"--- Tur {cycle} ---")

            # Ağırlıklı rastgele işlem seçimi (gerçekçi trafik karışımı)
            action = random.choices(
                ["read_user", "read_list", "read_product", "miss", "add", "delete", "session"],
                weights=[35, 20, 15, 10, 5, 5, 10]
            )[0]

            if action == "read_user":
                uid = random.choice(USER_IDS)
                get_user(uid)
                log(f"  → okundu: ucmd:user:{uid}")
            elif action == "read_list":
                get_user_list()
            elif action == "read_product":
                pid = random.choice(PRODUCT_IDS)
                get_product(pid)
                log(f"  → okundu: ucmd:product:{pid}")
            elif action == "miss":
                miss_traffic()
                log("  → kasıtlı MISS (olmayan key okundu)")
            elif action == "add":
                add_user()
            elif action == "delete":
                delete_user()
            elif action == "session":
                create_session()
                log("  → yeni session oluşturuldu (kısa TTL)")

            # Her 10 turda bir istatistik
            if cycle % 10 == 0:
                print_stats()

            time.sleep(1)   # saniyede ~1 işlem

    except KeyboardInterrupt:
        log("\nSimülatör durduruldu.")
        print_stats()


if __name__ == "__main__":
    main()