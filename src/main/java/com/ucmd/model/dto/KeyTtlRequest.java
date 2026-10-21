package com.ucmd.model.dto;

public class KeyTtlRequest {
    private String key;

    /** Hazelcast: entry'nin bulunduğu map. Boşsa key bir map adı sayılır. Redis'te kullanılmaz. */
    private String mapName;

    public KeyTtlRequest () {}
    public String getKey() {return key;}
    public void setKey(String key) {this.key = key;}

    public String getMapName() { return mapName; }
    public void setMapName(String mapName) { this.mapName = mapName; }
}
