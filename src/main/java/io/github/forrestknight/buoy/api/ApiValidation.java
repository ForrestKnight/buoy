package io.github.forrestknight.buoy.api;

public final class ApiValidation {

    /** Kebab-case resource keys: {@code checkout-v2}, {@code beta-testers}. */
    public static final String KEY_PATTERN = "^[a-z0-9]+(?:-[a-z0-9]+)*$";
    public static final String KEY_MESSAGE = "must be kebab-case: lowercase letters, digits, and single hyphens";

    private ApiValidation() {
    }
}
