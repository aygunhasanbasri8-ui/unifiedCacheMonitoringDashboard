package com.nkolay.ucmd.exception;

public class MonitorTimeoutException extends RuntimeException {
    public MonitorTimeoutException(String detail) {
        super("Monitor operation timed out: " + detail);
    }
}