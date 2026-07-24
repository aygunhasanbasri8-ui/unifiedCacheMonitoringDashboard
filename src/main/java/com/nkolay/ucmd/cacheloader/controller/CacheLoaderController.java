package com.nkolay.ucmd.cacheloader.controller;

import com.nkolay.ucmd.cacheloader.config.CacheLoaderProperties;
import com.nkolay.ucmd.cacheloader.exception.TableNotFoundException;
import com.nkolay.ucmd.cacheloader.model.dto.CacheFilter;
import com.nkolay.ucmd.cacheloader.model.dto.CacheKeysResponse;
import com.nkolay.ucmd.cacheloader.model.dto.TableInfo;
import com.nkolay.ucmd.cacheloader.model.dto.TableKeysResult;
import com.nkolay.ucmd.cacheloader.service.CacheLoaderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CacheLoader ekranı endpoint'leri. Bağımsız modül; core controller'lara bağımlı değil.
 * Auth: /v1/** interceptor tarafından kapsandığından JWT gerektirir (ek konfig yok).
 */
@RestController
@RequestMapping("/v1/cacheloader")
public class CacheLoaderController {

    // Filtre alanı olmayan, sadece kontrol amaçlı query paramlar.
    private static final Set<String> RESERVED = Set.of("tables");

    private final CacheLoaderService service;
    private final CacheLoaderProperties properties;

    public CacheLoaderController(CacheLoaderService service, CacheLoaderProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    /**
     * Config'de tanımlı tabloları ve filtrelenebilir alanlarını döner.
     * Ekran tablo seçimini bu uçtan besler (hardcode yok).
     */
    @GetMapping("/tables")
    public ResponseEntity<List<TableInfo>> listTables() {
        List<TableInfo> infos = properties.getTables().entrySet().stream()
                .map(e -> new TableInfo(
                        e.getKey(),
                        e.getValue().getKeyPrefix(),
                        e.getValue().getTtlSeconds(),
                        e.getValue().getFilterFields()))
                .toList();
        return ResponseEntity.ok(infos);
    }

    /**
     * Seçili tabloları DB'den cache'e seed eder ve key listesini döner.
     * tables boşsa tüm tablolar; filtre boşsa tüm kayıtlar.
     * Olmayan tablo -> 404 "table not found".
     */
    @GetMapping("/keys")
    public ResponseEntity<?> getKeys(
            @RequestParam(required = false) String tables,
            @RequestParam Map<String, String> allParams) {

        Set<String> tableSet = parseTables(tables);

        Map<String, String> fields = new LinkedHashMap<>(allParams);
        RESERVED.forEach(fields::remove);
        CacheFilter filter = CacheFilter.of(fields);

        try {
            List<TableKeysResult> results = service.seedAndListKeys(tableSet, filter);
            return ResponseEntity.ok(CacheKeysResponse.from(results));
        } catch (TableNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "table not found", "table", e.getTable()));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "invalid numeric filter value"));
        }
    }

    private Set<String> parseTables(String tables) {
        if (tables == null || tables.isBlank()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String t : Arrays.asList(tables.split(","))) {
            String trimmed = t.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
