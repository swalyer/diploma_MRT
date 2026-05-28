package com.diploma.mrt.integration.ml;

import com.diploma.mrt.entity.ArtifactStorageDisposition;
import com.diploma.mrt.entity.ArtifactType;
import com.diploma.mrt.entity.FindingType;
import com.diploma.mrt.entity.InferenceStatus;
import com.diploma.mrt.model.FindingLocation;
import com.diploma.mrt.model.MlMetrics;
import com.diploma.mrt.model.ReportData;
import com.diploma.mrt.report.CanonicalReportTextAssembler;
import com.diploma.mrt.service.materialization.CaseMaterialization;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MlInferenceResponse(
        String schemaVersion,
        InferenceStatus status,
        String modelVersion,
        MlMetrics metrics,
        ReportData reportData,
        List<Finding> findings,
        ArtifactOutputs artifacts
) {
    public record Finding(
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

    public MlInferenceResult toResult() {
        InferenceStatus resolvedStatus = Objects.requireNonNull(status, "status must not be null");
        if (resolvedStatus != InferenceStatus.COMPLETED) {
            return new MlInferenceResult(resolvedStatus, modelVersion, metrics, null);
        }
        ReportData resolvedReportData = Objects.requireNonNull(reportData, "reportData must not be null");
        CaseMaterialization materialization = new CaseMaterialization(
                CaseMaterialization.ArtifactReplaceMode.GENERATED_ONLY,
                materializedArtifacts(),
                materializedFindings(),
                new CaseMaterialization.ReportSpec(
                        CanonicalReportTextAssembler.assemble(Objects.requireNonNull(resolvedReportData.sections(), "reportData.sections must not be null")),
                        resolvedReportData
                )
        );
        return new MlInferenceResult(resolvedStatus, modelVersion, metrics, materialization);
    }

    private List<CaseMaterialization.ArtifactSpec> materializedArtifacts() {
        List<CaseMaterialization.ArtifactSpec> specs = new ArrayList<>();
        if (artifacts == null) {
            return specs;
        }
        appendArtifact(specs, ArtifactType.ENHANCED_VOLUME, artifacts.enhancedObjectKey(), "application/octet-stream");
        appendArtifact(specs, ArtifactType.LIVER_MASK, artifacts.liverMaskObjectKey(), "application/octet-stream");
        appendArtifact(specs, ArtifactType.LESION_MASK, artifacts.lesionMaskObjectKey(), "application/octet-stream");
        appendArtifact(specs, ArtifactType.LIVER_MESH, artifacts.liverMeshObjectKey(), "model/gltf-binary");
        appendArtifact(specs, ArtifactType.LESION_MESH, artifacts.lesionMeshObjectKey(), "model/gltf-binary");
        return specs;
    }

    private static void appendArtifact(List<CaseMaterialization.ArtifactSpec> specs, ArtifactType type, String objectKey, String mimeType) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        int slashIndex = objectKey.lastIndexOf('/');
        String fileName = slashIndex >= 0 ? objectKey.substring(slashIndex + 1) : objectKey;
        specs.add(new CaseMaterialization.ArtifactSpec(type, objectKey, fileName, mimeType, ArtifactStorageDisposition.MANAGED));
    }

    private List<CaseMaterialization.FindingSpec> materializedFindings() {
        if (findings == null) {
            return List.of();
        }
        return findings.stream()
                .map(finding -> new CaseMaterialization.FindingSpec(
                        finding.type(),
                        finding.label(),
                        finding.confidence(),
                        finding.sizeMm(),
                        finding.volumeMm3(),
                        finding.location()
                ))
                .toList();
    }
}
