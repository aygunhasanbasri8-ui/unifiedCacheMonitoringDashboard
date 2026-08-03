package com.nkolay.ucmd.exception;

public class InstanceBusyException extends RuntimeException {
    public InstanceBusyException(String detail) {
        super("Instance is busy: " + detail);
    }
}