package com.diploma.mrt.integration.ml.contract;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public final class MlContractTypes {
    private MlContractTypes() {
    }

    public enum Modality {
        CT("CT"),
        MRI("MRI");

        private final String wireValue;

        Modality(String wireValue) {
            this.wireValue = wireValue;
        }

        @JsonValue
        public String wireValue() {
            return wireValue;
        }

        @JsonCreator
        public static Modality fromWireValue(String rawValue) {
            return Arrays.stream(values())
                    .filter(value -> value.wireValue.equalsIgnoreCase(rawValue))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unsupported ML contract modality: " + rawValue));
        }
    }

    public enum ExecutionMode {
        MOCK("mock"),
        REAL("real");

        private final String wireValue;

        ExecutionMode(String wireValue) {
            this.wireValue = wireValue;
        }

        @JsonValue
        public String wireValue() {
            return wireValue;
        }

        @JsonCreator
        public static ExecutionMode fromWireValue(String rawValue) {
            return Arrays.stream(values())
                    .filter(value -> value.wireValue.equalsIgnoreCase(rawValue))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unsupported ML contract execution mode: " + rawValue));
        }
    }

    public enum InferenceStatus {
        STARTED("STARTED"),
        COMPLETED("COMPLETED"),
        FAILED("FAILED");

        private final String wireValue;

        InferenceStatus(String wireValue) {
            this.wireValue = wireValue;
        }

        @JsonValue
        public String wireValue() {
            return wireValue;
        }

        @JsonCreator
        public static InferenceStatus fromWireValue(String rawValue) {
            return Arrays.stream(values())
                    .filter(value -> value.wireValue.equalsIgnoreCase(rawValue))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unsupported ML contract inference status: " + rawValue));
        }
    }

    public enum FindingType {
        LESION("LESION");

        private final String wireValue;

        FindingType(String wireValue) {
            this.wireValue = wireValue;
        }

        @JsonValue
        public String wireValue() {
            return wireValue;
        }

        @JsonCreator
        public static FindingType fromWireValue(String rawValue) {
            return Arrays.stream(values())
                    .filter(value -> value.wireValue.equalsIgnoreCase(rawValue))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unsupported ML contract finding type: " + rawValue));
        }
    }
}
