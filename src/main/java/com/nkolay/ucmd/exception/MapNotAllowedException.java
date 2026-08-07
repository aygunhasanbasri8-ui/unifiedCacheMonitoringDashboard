package com.nkolay.ucmd.exception;

/** Rehber §5.7 — sorgulanan IMap izin verilen listede değil. */
public class MapNotAllowedException extends RuntimeException {

    public MapNotAllowedException(String detail) {
        super("Map not allowed: " + detail);
    }
}
