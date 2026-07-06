package io.github.forrestknight.buoy.service;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String entity, String key) {
        super(entity + " '" + key + "' not found");
    }
}
