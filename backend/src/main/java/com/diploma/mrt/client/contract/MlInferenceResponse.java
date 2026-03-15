package com.diploma.mrt.client.contract;

import com.diploma.mrt.entity.FindingType;
import com.diploma.mrt.entity.InferenceStatus;
import com.diploma.mrt.model.FindingLocation;
import com.diploma.mrt.model.MlMetrics;
import com.diploma.mrt.model.ReportData;

import java.util.List;

public record MlInferenceResponse(
        String schemaVersion,
        InferenceStatus status,
        String modelVersion,
        MlMetrics metrics,
        String reportText,
        ReportData reportData,
        List<MlFinding> findings,
        ArtifactOutputs artifacts
) {
    public record MlFinding(
            FindingType type,
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
