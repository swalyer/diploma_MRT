package com.diploma.mrt.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum ExecutionMode {
    MOCK("mock"),
    REAL("real");

    private final String value;

    ExecutionMode(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static ExecutionMode from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(mode -> mode.value.equalsIgnoreCase(rawValue) || mode.name().equalsIgnoreCase(rawValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported execution mode: " + rawValue));
    }
}
