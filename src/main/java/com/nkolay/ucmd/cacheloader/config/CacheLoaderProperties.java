package com.nkolay.ucmd.cacheloader.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * cacheloader-tables.yml dosyasındaki tablo tanımlarını bağlar.
 * prefix=cacheloader, tables anahtarı tablo adına göre map.
 */
@Configuration
@ConfigurationProperties(prefix = "cacheloader")
@PropertySource(
        value = "classpath:cacheloader-tables.yml",
        factory = YamlPropertySourceFactory.class
)
public class CacheLoaderProperties {

    /** Tablo adı -> tanım. Sıra korunsun diye LinkedHashMap. */
    private Map<String, TableDef> tables = new LinkedHashMap<>();

    public Map<String, TableDef> getTables() { return tables; }
    public void setTables(Map<String, TableDef> tables) { this.tables = tables; }

    /** Config'de tanımlı mı? */
    public boolean isKnownTable(String name) {
        return name != null && tables.containsKey(name);
    }

    public TableDef get(String name) {
        return tables.get(name);
    }

    public static class TableDef {
        private String keyPrefix;
        private long ttlSeconds = -1;          // -1 => TTL yok (kalıcı)
        private List<String> filterFields = List.of();

        public String getKeyPrefix() { return keyPrefix; }
        public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }

        public long getTtlSeconds() { return ttlSeconds; }
        public void setTtlSeconds(long ttlSeconds) { this.ttlSeconds = ttlSeconds; }

        public List<String> getFilterFields() { return filterFields; }
        public void setFilterFields(List<String> filterFields) { this.filterFields = filterFields; }
    }
}
