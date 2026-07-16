package com.nkolay.ucmd.exception;

public class InvalidPrefixException extends RuntimeException {
    public InvalidPrefixException(String detail) {
        super("Invalid prefix: " + detail);
    }
}