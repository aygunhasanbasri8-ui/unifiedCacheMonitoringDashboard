package com.nkolay.ucmd.exception;


public class MapNotAllowedException extends RuntimeException {

    public MapNotAllowedException(String detail) {
        super("Map not allowed: " + detail);
    }
}
