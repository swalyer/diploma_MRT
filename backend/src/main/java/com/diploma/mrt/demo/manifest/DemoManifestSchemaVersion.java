package com.diploma.mrt.demo.manifest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum DemoManifestSchemaVersion {
    V1("v1");

    private final String value;

    DemoManifestSchemaVersion(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static DemoManifestSchemaVersion from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(version -> version.value.equalsIgnoreCase(rawValue) || version.name().equalsIgnoreCase(rawValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported demo manifest schema version: " + rawValue));
    }
}
