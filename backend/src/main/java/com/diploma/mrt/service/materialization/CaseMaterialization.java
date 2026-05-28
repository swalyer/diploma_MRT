package com.diploma.mrt.service.materialization;

import com.diploma.mrt.entity.ArtifactStorageDisposition;
import com.diploma.mrt.entity.ArtifactType;
import com.diploma.mrt.entity.FindingType;
import com.diploma.mrt.model.FindingLocation;
import com.diploma.mrt.model.ReportData;

import java.util.List;
import java.util.Objects;

public record CaseMaterialization(
        ArtifactReplaceMode artifactReplaceMode,
        List<ArtifactSpec> artifacts,
        List<FindingSpec> findings,
        ReportSpec report
) {
    public CaseMaterialization {
        artifactReplaceMode = Objects.requireNonNull(artifactReplaceMode, "artifactReplaceMode must not be null");
        artifacts = List.copyOf(artifacts == null ? List.of() : artifacts);
        findings = List.copyOf(findings == null ? List.of() : findings);
        report = Objects.requireNonNull(report, "report must not be null");
    }

    public enum ArtifactReplaceMode {
        GENERATED_ONLY,
        ALL_CASE_ARTIFACTS
    }

    public record ArtifactSpec(
            ArtifactType type,
            String objectKey,
            String originalFileName,
            String mimeType,
            ArtifactStorageDisposition storageDisposition
    ) {
        public ArtifactSpec {
            type = Objects.requireNonNull(type, "type must not be null");
            if (objectKey == null || objectKey.isBlank()) {
                throw new IllegalArgumentException("objectKey must not be blank");
            }
            if (originalFileName == null || originalFileName.isBlank()) {
                throw new IllegalArgumentException("originalFileName must not be blank");
            }
            if (mimeType == null || mimeType.isBlank()) {
                throw new IllegalArgumentException("mimeType must not be blank");
            }
            storageDisposition = Objects.requireNonNull(storageDisposition, "storageDisposition must not be null");
        }
    }

    public record FindingSpec(
            FindingType type,
            String label,
            Double confidence,
            Double sizeMm,
            Double volumeMm3,
            FindingLocation location
    ) {
        public FindingSpec {
            type = Objects.requireNonNull(type, "type must not be null");
            if (label == null || label.isBlank()) {
                throw new IllegalArgumentException("label must not be blank");
            }
        }
    }

    public record ReportSpec(
            String reportText,
            ReportData reportData
    ) {
        public ReportSpec {
            reportText = reportText == null ? "" : reportText;
            reportData = Objects.requireNonNull(reportData, "reportData must not be null");
        }
    }
}
