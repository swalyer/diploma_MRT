package com.diploma.mrt.integration.ml;

import com.diploma.mrt.entity.ExecutionMode;
import com.diploma.mrt.entity.Modality;

import java.util.UUID;

public record MlInferenceRequest(
        String schemaVersion,
        Long caseId,
        Modality modality,
        ExecutionMode executionMode,
        FileReferences fileReferences,
        RequestMetadata requestMetadata
) {
    public static final String CURRENT_SCHEMA_VERSION = "v1";

    public record FileReferences(String inputObjectKey) {}

    public record RequestMetadata(String requestId, Long runId) {}

    public static MlInferenceRequest of(Long caseId, Long runId, Modality modality, String inputObjectKey, ExecutionMode executionMode) {
        return new MlInferenceRequest(
                CURRENT_SCHEMA_VERSION,
                caseId,
                modality,
                executionMode,
                new FileReferences(inputObjectKey),
                new RequestMetadata(UUID.randomUUID().toString(), runId)
        );
    }

    public MlInferenceRequest withDefaultExecutionMode(ExecutionMode fallback) {
        return executionMode != null ? this : new MlInferenceRequest(
                schemaVersion,
                caseId,
                modality,
                fallback,
                fileReferences,
                requestMetadata
        );
    }
}
