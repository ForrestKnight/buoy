package io.github.forrestknight.buoy.service;

public class DuplicateKeyException extends RuntimeException {

    public DuplicateKeyException(String entity, String key) {
        super(entity + " with key '" + key + "' already exists");
    }
}
