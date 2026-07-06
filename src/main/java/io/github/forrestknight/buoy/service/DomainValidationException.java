package io.github.forrestknight.buoy.service;

/**
 * Semantic validation failure that jakarta.validation annotations can't express
 * (rollout weights, segment references, operator/attribute combinations).
 */
public class DomainValidationException extends RuntimeException {

    public DomainValidationException(String message) {
        super(message);
    }
}
