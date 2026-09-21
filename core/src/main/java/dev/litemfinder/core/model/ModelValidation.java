package dev.litemfinder.core.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

final class ModelValidation {

    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH_PATTERN = Pattern.compile("[a-z0-9/._-]+");

    private ModelValidation() {
    }

    static String namespace(String value) {
        return matching(value, "namespace", NAMESPACE_PATTERN);
    }

    static String path(String value) {
        return matching(value, "path", PATH_PATTERN);
    }

    static String nonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (!value.equals(value.trim())) {
            throw new IllegalArgumentException(fieldName + " must not have surrounding whitespace");
        }
        return value;
    }

    static Map<String, String> immutableMetadata(Map<String, String> metadata) {
        Objects.requireNonNull(metadata, "metadata must not be null");
        Map<String, String> copy = new LinkedHashMap<>();
        metadata.forEach((key, value) -> copy.put(
                nonBlank(key, "metadata key"),
                Objects.requireNonNull(value, "metadata value must not be null")
        ));
        return Map.copyOf(copy);
    }

    private static String matching(String value, String fieldName, Pattern pattern) {
        nonBlank(value, fieldName);
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " contains unsupported characters: " + value);
        }
        return value;
    }
}
