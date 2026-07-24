package com.nkolay.ucmd.cacheloader.service;

import com.nkolay.ucmd.cacheloader.model.dto.CacheFilter;
import com.nkolay.ucmd.cacheloader.model.dto.CacheRecord;

import java.util.List;

/**
 * Bir DB tablosunu cache'e taşımak için erişim soyutlaması.
 * Yeni tablo eklemek = yeni bir @Component TableAccessor + entity + yml kaydı.
 * Service bu bean'leri tableName() üzerinden map'ler.
 */
public interface TableAccessor {

    /** cacheloader-tables.yml'deki tablo adı ile birebir eşleşmeli. */
    String tableName();

    /** Filtreyi uygular, eşleşen satırları (id + payload) döner. Filtre boşsa tümü. */
    List<CacheRecord> fetch(CacheFilter filter);
}
