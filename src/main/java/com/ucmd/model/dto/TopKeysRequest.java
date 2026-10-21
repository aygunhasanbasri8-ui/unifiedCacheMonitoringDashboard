package com.ucmd.model.dto;

public class TopKeysRequest {

    public static final int MAX_N = 1000;
    private int n = 20;
    private Integer scanLimit;

    public TopKeysRequest() {}

    public int getN() {
        if (n <= 0) return 20;
        return Math.min(n, MAX_N);
    }

    public void setN(int n) {this.n = n;}

    public Integer getScanLimit() {return scanLimit;}
    public void setScanLimit(Integer scanLimit) {this.scanLimit = scanLimit;}
}
