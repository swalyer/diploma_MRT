package com.diploma.mrt.integration.ml.contract;

import java.util.List;

public record MlContractInferenceResponse(
        String schemaVersion,
        MlContractTypes.InferenceStatus status,
        String modelVersion,
        Metrics metrics,
        String reportText,
        ReportData reportData,
        List<Finding> findings,
        ArtifactOutputs artifacts
) {
    public record BoundingBox(
            List<Integer> min,
            List<Integer> max
    ) {}

    public record FindingLocation(
            String segment,
            List<Double> centroid,
            BoundingBox bbox,
            List<Integer> extent,
            String suspicion
    ) {}

    public record Metrics(
            MlContractTypes.ExecutionMode mode,
            Boolean liverModel,
            Boolean lesionModel,
            Boolean medsamAvailable,
            Boolean supportsMri3dSuspiciousZone
    ) {}

    public record ReportSections(
            String findings,
            String impression,
            String limitations,
            String recommendation
    ) {}

    public record ReportCapabilities(
            Boolean supports3dLiver,
            Boolean supports3dLesion
    ) {}

    public record ReportData(
            MlContractTypes.Modality modality,
            MlContractTypes.ExecutionMode executionMode,
            Integer lesionCount,
            Boolean evidenceBound,
            ReportSections sections,
            ReportCapabilities capabilities
    ) {}

    public record Finding(
            MlContractTypes.FindingType type,
            String label,
            Double confidence,
            Double sizeMm,
            Double volumeMm3,
            FindingLocation location
    ) {}

    public record ArtifactOutputs(
            String enhancedObjectKey,
            String liverMaskObjectKey,
            String lesionMaskObjectKey,
            String liverMeshObjectKey,
            String lesionMeshObjectKey
    ) {}
}
