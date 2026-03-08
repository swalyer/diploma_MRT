package com.diploma.mrt.service.impl;

import com.diploma.mrt.client.MlClient;
import com.diploma.mrt.dto.CaseDtos;
import com.diploma.mrt.entity.*;
import com.diploma.mrt.exception.AccessDeniedException;
import com.diploma.mrt.exception.NotFoundException;
import com.diploma.mrt.repository.*;
import com.diploma.mrt.service.AuditService;
import com.diploma.mrt.service.CaseService;
import com.diploma.mrt.service.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CaseServiceImpl implements CaseService {
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final ArtifactRepository artifactRepository;
    private final FindingRepository findingRepository;
    private final ReportRepository reportRepository;
    private final InferenceRunRepository inferenceRunRepository;
    private final StorageService storageService;
    private final MlClient mlClient;
    private final AuditService auditService;
    private final String executionMode;

    public CaseServiceImpl(CaseRepository caseRepository, UserRepository userRepository, ArtifactRepository artifactRepository, FindingRepository findingRepository, ReportRepository reportRepository, InferenceRunRepository inferenceRunRepository, StorageService storageService, MlClient mlClient, AuditService auditService, @Value("${app.ml-mode:mock}") String executionMode) {
        this.caseRepository = caseRepository;
        this.userRepository = userRepository;
        this.artifactRepository = artifactRepository;
        this.findingRepository = findingRepository;
        this.reportRepository = reportRepository;
        this.inferenceRunRepository = inferenceRunRepository;
        this.storageService = storageService;
        this.mlClient = mlClient;
        this.auditService = auditService;
        this.executionMode = executionMode;
    }

    @Override
    public CaseDtos.CaseResponse create(String userEmail, CaseDtos.CreateCaseRequest request) {
        User user = findUser(userEmail);
        CaseEntity caseEntity = new CaseEntity();
        caseEntity.setPatientPseudoId(request.patientPseudoId());
        caseEntity.setModality(request.modality());
        caseEntity.setStatus(CaseStatus.CREATED);
        caseEntity.setCreatedBy(user);
        caseEntity.setCreatedAt(Instant.now());
        caseEntity.setUpdatedAt(Instant.now());
        CaseEntity saved = caseRepository.save(caseEntity);
        auditService.log(user.getId(), saved.getId(), "CASE_CREATED", "{}");
        return map(saved);
    }

    @Override
    public List<CaseDtos.CaseResponse> list(String userEmail, CaseStatus status) {
        User user = findUser(userEmail);
        List<CaseEntity> cases = status == null ? caseRepository.findAll() : caseRepository.findByStatus(status);
        return cases.stream().filter(c -> c.getCreatedBy().getId().equals(user.getId())).map(this::map).toList();
    }

    @Override
    public CaseDtos.CaseResponse get(String userEmail, Long id) {
        return map(findOwnedCase(userEmail, id));
    }

    @Override
    @Transactional
    public CaseDtos.ArtifactResponse upload(String userEmail, Long id, MultipartFile file) {
        CaseEntity caseEntity = findOwnedCase(userEmail, id);
        validateFile(file.getOriginalFilename());
        String objectKey = storageService.saveCaseFile(id, file);
        Artifact artifact = new Artifact();
        artifact.setCaseEntity(caseEntity);
        artifact.setType("ORIGINAL_INPUT");
        artifact.setFilePath(objectKey);
        artifact.setMimeType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        artifact.setCreatedAt(Instant.now());
        Artifact saved = artifactRepository.save(artifact);
        caseEntity.setStatus(CaseStatus.UPLOADED);
        caseEntity.setUpdatedAt(Instant.now());
        auditService.log(caseEntity.getCreatedBy().getId(), id, "CASE_UPLOADED", "{}");
        return toArtifactResponse(saved);
    }

    @Override
    public void process(String userEmail, Long id) {
        findOwnedCase(userEmail, id);
        processAsync(id);
    }

    @Async
    @Transactional
    public void processAsync(Long id) {
        CaseEntity caseEntity = caseRepository.findById(id).orElseThrow(() -> new NotFoundException("Case not found"));
        caseEntity.setStatus(CaseStatus.PROCESSING);
        InferenceRun run = new InferenceRun();
        run.setCaseEntity(caseEntity);
        run.setStatus(InferenceStatus.STARTED);
        run.setModelVersion("pipeline");
        auditService.log(caseEntity.getCreatedBy().getId(), id, "INFERENCE_STARTED", "{}");
        run.setStartedAt(Instant.now());
        inferenceRunRepository.save(run);
        try {
            Artifact original = artifactRepository.findByCaseEntityId(id).stream().filter(a -> "ORIGINAL_INPUT".equals(a.getType())).findFirst().orElseThrow(() -> new NotFoundException("Input missing"));
            auditService.log(caseEntity.getCreatedBy().getId(), id, "INFERENCE_REQUEST_SENT", "{\"stage\":\"ml_request_sent\"}");
            CaseDtos.MlResult result = mlClient.infer(id, caseEntity.getModality().name(), original.getFilePath());
            persistMlResult(caseEntity, result);
            run.setModelVersion(result.modelVersion());
            run.setMetricsJson(result.metricsJson());
            run.setStatus(InferenceStatus.COMPLETED);
            auditService.log(caseEntity.getCreatedBy().getId(), id, "INFERENCE_COMPLETED", "{}");
            caseEntity.setStatus(CaseStatus.COMPLETED);
        } catch (Exception exception) {
            run.setStatus(InferenceStatus.FAILED);
            caseEntity.setStatus(CaseStatus.FAILED);
            String failureDetails = buildFailureDetails(exception);
            run.setMetricsJson(failureDetails);
            auditService.log(caseEntity.getCreatedBy().getId(), id, "INFERENCE_FAILED", failureDetails);
        }
        run.setFinishedAt(Instant.now());
        caseEntity.setUpdatedAt(Instant.now());
    }

    private String buildFailureDetails(Exception exception) {
        if (exception instanceof RestClientResponseException responseException) {
            return "{\"stage\":\"ml_request_validation_failed\",\"message\":\"ml-service rejected inference request (" + responseException.getRawStatusCode() + ")\",\"httpStatus\":" + responseException.getRawStatusCode() + ",\"error\":\"" + responseException.getClass().getSimpleName() + "\"}";
        }
        String message = exception.getMessage() == null ? "unexpected error" : exception.getMessage().replace("\"", "'");
        return "{\"stage\":\"inference_failed\",\"message\":\"" + message + "\",\"error\":\"" + exception.getClass().getSimpleName() + "\"}";
    }

    private void persistMlResult(CaseEntity caseEntity, CaseDtos.MlResult result) {
        addArtifact(caseEntity, "ENHANCED", result.enhancedObjectKey(), "application/octet-stream");
        addArtifact(caseEntity, "LIVER_MASK", result.liverMaskObjectKey(), "application/octet-stream");
        addArtifact(caseEntity, "LESION_MASK", result.lesionMaskObjectKey(), "application/octet-stream");
        addArtifact(caseEntity, "LIVER_MESH", result.liverMeshObjectKey(), "model/gltf-binary");
        addArtifact(caseEntity, "LESION_MESH", result.lesionMeshObjectKey(), "model/gltf-binary");
        for (CaseDtos.MlFinding f : result.findings()) {
            Finding finding = new Finding();
            finding.setCaseEntity(caseEntity);
            finding.setType(f.type());
            finding.setLabel(f.label());
            finding.setConfidence(f.confidence());
            finding.setSizeMm(f.sizeMm());
            finding.setVolumeMm3(f.volumeMm3());
            finding.setLocationJson(f.locationJson());
            findingRepository.save(finding);
        }
        Report report = new Report();
        report.setCaseEntity(caseEntity);
        report.setReportText(result.reportText());
        report.setReportJson(result.reportJson());
        report.setCreatedAt(Instant.now());
        reportRepository.save(report);
    }

    private void addArtifact(CaseEntity caseEntity, String type, String objectKey, String mimeType) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        Artifact artifact = new Artifact();
        artifact.setCaseEntity(caseEntity);
        artifact.setType(type);
        artifact.setFilePath(objectKey);
        artifact.setMimeType(mimeType);
        artifact.setCreatedAt(Instant.now());
        artifactRepository.save(artifact);
    }

    private void validateFile(String filename) {
        if (filename == null || !(filename.endsWith(".nii") || filename.endsWith(".nii.gz") || filename.endsWith(".zip") || filename.endsWith(".dcm"))) {
            throw new RuntimeException("Unsupported file format");
        }
    }

    @Override
    public void delete(String userEmail, Long id) {
        CaseEntity caseEntity = findOwnedCase(userEmail, id);
        caseRepository.delete(caseEntity);
    }

    @Override
    public List<CaseDtos.ArtifactResponse> artifacts(String userEmail, Long id) {
        findOwnedCase(userEmail, id);
        return artifactRepository.findByCaseEntityId(id).stream().map(this::toArtifactResponse).toList();
    }

    @Override
    public List<CaseDtos.FindingResponse> findings(String userEmail, Long id) {
        findOwnedCase(userEmail, id);
        return findingRepository.findByCaseEntityId(id).stream().map(f -> new CaseDtos.FindingResponse(f.getId(), f.getType(), f.getLabel(), f.getConfidence(), f.getSizeMm(), f.getVolumeMm3(), f.getLocationJson())).toList();
    }

    @Override
    public CaseDtos.ReportResponse report(String userEmail, Long id) {
        findOwnedCase(userEmail, id);
        Report report = reportRepository.findByCaseEntityId(id).orElseThrow(() -> new NotFoundException("Report not found"));
        return new CaseDtos.ReportResponse(report.getReportText(), report.getReportJson());
    }

    @Override
    public CaseDtos.StatusResponse status(String userEmail, Long id) {
        CaseEntity caseEntity = findOwnedCase(userEmail, id);
        InferenceRun run = inferenceRunRepository.findByCaseEntityId(id).stream().findFirst().orElse(null);
        List<Map<String, String>> stages = auditService.listByCase(id).stream().map(a -> {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("action", a.getAction());
            row.put("at", a.getCreatedAt().toString());
            row.put("details", a.getDetailsJson());
            return row;
        }).toList();
        return new CaseDtos.StatusResponse(
                id,
                caseEntity.getStatus(),
                run == null ? "N/A" : run.getStatus().name(),
                executionMode,
                run == null ? "N/A" : run.getModelVersion(),
                run == null ? null : run.getMetricsJson(),
                stages
        );
    }

    @Override
    public CaseDtos.Viewer3DResponse viewer3d(String userEmail, Long id) {
        findOwnedCase(userEmail, id);
        List<Artifact> artifacts = artifactRepository.findByCaseEntityId(id);
        Long liver = artifacts.stream().filter(a -> "LIVER_MESH".equals(a.getType())).map(Artifact::getId).findFirst().orElse(null);
        Long lesion = artifacts.stream().filter(a -> "LESION_MESH".equals(a.getType())).map(Artifact::getId).findFirst().orElse(null);
        return new CaseDtos.Viewer3DResponse(liver, lesion);
    }

    private CaseDtos.ArtifactResponse toArtifactResponse(Artifact artifact) {
        String objectKey = artifact.getFilePath();
        String[] parts = objectKey.split("/");
        String fileName = parts[parts.length - 1];
        return new CaseDtos.ArtifactResponse(artifact.getId(), artifact.getType(), artifact.getMimeType(), fileName, "/api/files/" + artifact.getId() + "/download");
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new NotFoundException("User not found"));
    }

    private CaseEntity findOwnedCase(String userEmail, Long id) {
        CaseEntity caseEntity = caseRepository.findById(id).orElseThrow(() -> new NotFoundException("Case not found"));
        if (!caseEntity.getCreatedBy().getEmail().equals(userEmail)) {
            throw new AccessDeniedException("Access denied");
        }
        return caseEntity;
    }

    private CaseDtos.CaseResponse map(CaseEntity caseEntity) {
        return new CaseDtos.CaseResponse(caseEntity.getId(), caseEntity.getPatientPseudoId(), caseEntity.getModality(), caseEntity.getStatus(), caseEntity.getCreatedAt(), caseEntity.getUpdatedAt());
    }
}
