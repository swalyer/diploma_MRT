package com.diploma.mrt.integration.ml.contract;

public record MlContractInferenceRequest(
        String schemaVersion,
        Long caseId,
        MlContractTypes.Modality modality,
        MlContractTypes.ExecutionMode executionMode,
        FileReferences fileReferences,
        RequestMetadata requestMetadata
) {
    public record FileReferences(String inputObjectKey) {}
    public record RequestMetadata(String requestId, Long runId) {}
}
