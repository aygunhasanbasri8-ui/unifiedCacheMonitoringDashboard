package com.nkolay.ucmd.exception;

public class CircuitOpenException extends RuntimeException {
    public CircuitOpenException(String detail) {
        super("Circuit breaker is open: " + detail);
    }
}