package com.ucmd.exception;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String detail) {
        super("Rate limit exceeded: " + detail);
    }
}