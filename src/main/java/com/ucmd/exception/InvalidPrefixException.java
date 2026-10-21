package com.ucmd.exception;

public class InvalidPrefixException extends RuntimeException {
    public InvalidPrefixException(String detail) {
        super("Invalid prefix: " + detail);
    }
}