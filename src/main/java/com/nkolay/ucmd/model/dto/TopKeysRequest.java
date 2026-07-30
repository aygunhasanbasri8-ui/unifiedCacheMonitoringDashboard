package com.nkolay.ucmd.model.dto;

public class TopKeysRequest {
    private int n = 20;
    public TopKeysRequest() {}
    public int getN() {return n <= 0 ? 20 : n;}
    public void setN(int n) {this.n = n;}
}
