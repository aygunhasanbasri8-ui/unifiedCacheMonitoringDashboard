package com.nkolay.ucmd.cacheloader.exception;

/**
 * Config'de (cacheloader-tables.yml) tanımlı olmayan bir tablo istendiğinde fırlatılır.
 * Controller içinde 404 + "table not found" olarak karşılanır (core handler'a bağımlı değil).
 */
public class TableNotFoundException extends RuntimeException {
    private final String table;

    public TableNotFoundException(String table) {
        super("Tablo bulunamadı: " + table);
        this.table = table;
    }

    public String getTable() { return table; }
}
