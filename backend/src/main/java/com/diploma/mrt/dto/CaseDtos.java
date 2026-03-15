package com.diploma.mrt.dto;

import com.diploma.mrt.entity.ArtifactType;
import com.diploma.mrt.entity.AuditAction;
import com.diploma.mrt.entity.CaseStatus;
import com.diploma.mrt.entity.CaseOrigin;
import com.diploma.mrt.entity.DemoCategory;
import com.diploma.mrt.entity.ExecutionMode;
import com.diploma.mrt.entity.FindingType;
import com.diploma.mrt.entity.InferenceStatus;
import com.diploma.mrt.entity.Modality;
import com.diploma.mrt.model.FindingLocation;
import com.diploma.mrt.model.MlMetrics;
import com.diploma.mrt.model.ProcessDetails;
import com.diploma.mrt.model.ReportData;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public class CaseDtos {
    public record CreateCaseRequest(@NotBlank String patientPseudoId, @NotNull Modality modality) {}
    public record CaseResponse(Long id, String patientPseudoId, Modality modality, CaseStatus status, InferenceStatus inferenceStatus,
                               ExecutionMode executionMode, CaseOrigin origin, DemoCategory demoCategory, String demoCaseSlug,
                               String demoManifestVersion, String sourceDataset, String sourceAttribution, Instant createdAt, Instant updatedAt) {}
    public record ArtifactResponse(Long id, ArtifactType type, String mimeType, String fileName, String downloadUrl) {}
    public record FindingResponse(Long id, FindingType type, String label, Double confidence, Double sizeMm, Double volumeMm3, FindingLocation location) {}
    public record ReportResponse(String reportText, ReportData reportData) {}
    public record StageAuditEvent(AuditAction action, Instant at, ProcessDetails details) {}
    public record StatusResponse(Long caseId, CaseStatus status, InferenceStatus inferenceStatus, ExecutionMode executionMode, String modelVersion,
                                 MlMetrics metrics, ProcessDetails failureDetails, List<StageAuditEvent> stageAuditTrail) {}
    public record Viewer3DResponse(Long liverMeshArtifactId, Long lesionMeshArtifactId) {}
}
