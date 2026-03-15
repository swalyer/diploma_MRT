package com.diploma.mrt.client.contract;

import com.diploma.mrt.entity.ExecutionMode;
import com.diploma.mrt.entity.Modality;

public record MlInferenceRequest(
        String schemaVersion,
        Long caseId,
        Modality modality,
        ExecutionMode executionMode,
        FileReferences fileReferences,
        RequestMetadata requestMetadata
) {
    public record FileReferences(String inputObjectKey) {}
    public record RequestMetadata(String requestId, Long runId) {}
}
