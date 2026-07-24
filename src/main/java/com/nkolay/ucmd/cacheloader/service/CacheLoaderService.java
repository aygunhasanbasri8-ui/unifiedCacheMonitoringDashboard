package com.nkolay.ucmd.cacheloader.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nkolay.ucmd.cacheloader.config.CacheLoaderProperties;
import com.nkolay.ucmd.cacheloader.config.CacheLoaderProperties.TableDef;
import com.nkolay.ucmd.cacheloader.exception.TableNotFoundException;
import com.nkolay.ucmd.cacheloader.model.dto.CacheFilter;
import com.nkolay.ucmd.cacheloader.model.dto.CacheRecord;
import com.nkolay.ucmd.cacheloader.model.dto.TableKeysResult;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * DB -> Redis seed akışı ve seçili tabloların key listesini üretir.
 * Tamamen bağımsız modül: core servislere bağımlı değil, sadece RedisTemplate ve
 * modül içi bileşenleri kullanır.
 */
@Service
public class CacheLoaderService {

    private final CacheLoaderProperties properties;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, TableAccessor> accessors;

    public CacheLoaderService(CacheLoaderProperties properties,
                              RedisTemplate<String, String> redisTemplate,
                              ObjectMapper objectMapper,
                              List<TableAccessor> tableAccessors) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.accessors = tableAccessors.stream()
                .collect(Collectors.toMap(TableAccessor::tableName, Function.identity(),
                        (a, b) -> a, LinkedHashMap::new));
    }

    /**
     * Seçili tabloları DB'den çekip Redis'e yazar ve yazılan key listesini döner.
     * Tablo seçilmezse config'deki tüm tablolar işlenir.
     * İstenen tablo config'de yoksa hiçbir yazma yapılmadan TableNotFoundException fırlatılır.
     */
    public List<TableKeysResult> seedAndListKeys(Collection<String> requestedTables, CacheFilter filter) {
        Collection<String> targets = (requestedTables == null || requestedTables.isEmpty())
                ? properties.getTables().keySet()
                : requestedTables;

        // Fail-fast: kısmi yazma olmasın diye önce tüm tabloları doğrula.
        for (String table : targets) {
            if (!properties.isKnownTable(table) || !accessors.containsKey(table)) {
                throw new TableNotFoundException(table);
            }
        }

        List<TableKeysResult> results = new ArrayList<>(targets.size());
        for (String table : targets) {
            results.add(seedTable(table, filter));
        }
        return results;
    }

    private TableKeysResult seedTable(String table, CacheFilter filter) {
        TableDef def = properties.get(table);
        TableAccessor accessor = accessors.get(table);

        List<CacheRecord> records = accessor.fetch(filter);
        final long ttlSeconds = def.getTtlSeconds();      // >0 ise SETEX, değilse kalıcı
        final String prefix = def.getKeyPrefix();

        // Serileştirme pipeline dışında yapılır; pipeline sadece I/O yapar.
        final List<String> keys = new ArrayList<>(records.size());
        final List<String> values = new ArrayList<>(records.size());
        for (CacheRecord record : records) {
            keys.add(prefix + record.id());
            values.add(toJson(record.payload()));
        }

        if (!keys.isEmpty()) {
            // Tek round-trip'te toplu yazım (N ayrı SET yerine pipeline).
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                var stringCommands = connection.stringCommands();
                for (int i = 0; i < keys.size(); i++) {
                    byte[] k = keys.get(i).getBytes(StandardCharsets.UTF_8);
                    byte[] v = values.get(i).getBytes(StandardCharsets.UTF_8);
                    if (ttlSeconds > 0) {
                        stringCommands.setEx(k, ttlSeconds, v);
                    } else {
                        stringCommands.set(k, v);
                    }
                }
                return null; // pipeline callback null dönmeli
            });
        }

        return new TableKeysResult(table, keys.size(), keys);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cache value serileştirilemedi: " + e.getMessage(), e);
        }
    }
}
