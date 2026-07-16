package com.nkolay.ucmd.exception;

public class InstanceUnreachableException extends RuntimeException {
    public InstanceUnreachableException(String name) {
        super("Instance is not reachable: " + name);
    }
}
