package com.nkolay.ucmd.exception;

public class InstanceNotFoundException extends RuntimeException {
    public InstanceNotFoundException(Long id) {
        super("Instance not found with id: " + id);
    }
}