package com.nkolay.ucmd.model.dto;

public class SearchKeysRequest {
    private String prefix = "";
    private String cursor = "0";
    private int count = 20;

    public SearchKeysRequest() {}

    public String getPrefix () { return prefix == null ? "" : prefix; }
    public void setPrefix(String prefix ) { this.prefix = prefix;}

    public String getCursor () { return (cursor == null || cursor.isEmpty()) ? "0" : cursor; }
    public void setCursor (String cursor) { this.cursor = cursor;}

    public int getCount () { return count <= 0 ? 20 : count;}
    public void setCount ( int count) { this.count = count; }
}
