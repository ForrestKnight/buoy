package io.github.forrestknight.buoy.service;

public class StaleVersionException extends RuntimeException {

    public StaleVersionException(long expected, long actual) {
        super("Version conflict: request based on version " + expected
                + " but current version is " + actual + "; re-fetch and retry");
    }
}
