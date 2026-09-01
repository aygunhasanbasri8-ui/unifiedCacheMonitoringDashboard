package com.nkolay.ucmd.model.dto;

import com.nkolay.ucmd.enums.MatchMode;

public class SearchKeysRequest {
    private String prefix = "";
    private String cursor = "0";
    private int count = 20;
    private MatchMode matchMode = MatchMode.STARTS_WITH;

    /** Hazelcast: hangi map içinde aranacak. Redis'te kullanılmaz. */
    private String mapName;


    public SearchKeysRequest() {}

    public String getMapName() { return mapName; }
    public void setMapName(String mapName) { this.mapName = mapName; }

    public String getPrefix () {return prefix;}
    public void setPrefix(String prefix ) { this.prefix = prefix;}

    public String getCursor () { return (cursor == null || cursor.isEmpty()) ? "0" : cursor; }
    public void setCursor (String cursor) { this.cursor = cursor;}

    public static final int MAX_COUNT = 1000;

    public int getCount() {
        if (count <= 0) return 20;
        return Math.min(count, MAX_COUNT);
    }
    public void setCount ( int count) { this.count = count; }

    public MatchMode getMatchMode() { return matchMode == null ? MatchMode.STARTS_WITH : matchMode;}
    public void setMatchMode(MatchMode matchMode) { this.matchMode = matchMode;}

}
