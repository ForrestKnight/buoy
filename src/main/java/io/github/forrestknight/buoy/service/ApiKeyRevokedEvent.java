package io.github.forrestknight.buoy.service;

/** Published when a key is revoked so authentication caches can drop it after commit. */
public record ApiKeyRevokedEvent(String tokenHash) {
}
