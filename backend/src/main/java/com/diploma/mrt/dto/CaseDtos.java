package com.diploma.mrt.dto;

import com.diploma.mrt.entity.CaseStatus;
import com.diploma.mrt.entity.Modality;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public class CaseDtos {
    public record CreateCaseRequest(@NotBlank String patientPseudoId, @NotNull Modality modality) {}
    public record CaseResponse(Long id, String patientPseudoId, Modality modality, CaseStatus status, Instant createdAt, Instant updatedAt) {}
    public record ArtifactResponse(Long id, String type, String filePath, String mimeType) {}
    public record FindingResponse(Long id, String type, String label, Double confidence, Double sizeMm, Double volumeMm3, String locationJson) {}
    public record ReportResponse(String reportText, String reportJson) {}
    public record StatusResponse(Long caseId, CaseStatus status) {}
    public record Viewer3DResponse(String liverMeshPath, String lesionMeshPath) {}
    public record MlFinding(String type, String label, Double confidence, Double sizeMm, Double volumeMm3, String locationJson) {}
    public record MlResult(String modelVersion, String metricsJson, String reportText, String reportJson, List<MlFinding> findings, String enhancedPath, String liverMaskPath, String lesionMaskPath, String liverMeshPath, String lesionMeshPath) {}
}
