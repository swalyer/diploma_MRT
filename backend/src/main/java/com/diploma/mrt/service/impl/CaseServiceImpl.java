package com.diploma.mrt.service.impl;

import com.diploma.mrt.dto.CaseDtos;
import com.diploma.mrt.entity.Artifact;
import com.diploma.mrt.entity.ArtifactStorageDisposition;
import com.diploma.mrt.entity.ArtifactType;
import com.diploma.mrt.entity.AuditAction;
import com.diploma.mrt.entity.CaseEntity;
import com.diploma.mrt.entity.CaseOrigin;
import com.diploma.mrt.entity.CaseStatus;
import com.diploma.mrt.entity.ExecutionMode;
import com.diploma.mrt.entity.InferenceRun;
import com.diploma.mrt.entity.Report;
import com.diploma.mrt.entity.User;
import com.diploma.mrt.exception.BadRequestException;
import com.diploma.mrt.exception.ConflictException;
import com.diploma.mrt.exception.NotFoundException;
import com.diploma.mrt.repository.ArtifactRepository;
import com.diploma.mrt.repository.CaseRepository;
import com.diploma.mrt.repository.FindingRepository;
import com.diploma.mrt.repository.InferenceRunRepository;
import com.diploma.mrt.repository.ReportRepository;
import com.diploma.mrt.service.AuditService;
import com.diploma.mrt.service.CaseService;
import com.diploma.mrt.service.StorageService;
import com.diploma.mrt.util.EmailNormalizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CaseServiceImpl implements CaseService {
    private final CaseRepository caseRepository;
    private final ArtifactRepository artifactRepository;
    private final FindingRepository findingRepository;
    private final ReportRepository reportRepository;
    private final InferenceRunRepository inferenceRunRepository;
    private final StorageService storageService;
    private final AuditService auditService;
    private final CaseProcessingService caseProcessingService;
    private final CaseAccessService caseAccessService;
    private final CaseFileService caseFileService;
    private final ExecutionMode defaultExecutionMode;

    public CaseServiceImpl(
            CaseRepository caseRepository,
            ArtifactRepository artifactRepository,
            FindingRepository findingRepository,
            ReportRepository reportRepository,
            InferenceRunRepository inferenceRunRepository,
            StorageService storageService,
            AuditService auditService,
            CaseProcessingService caseProcessingService,
            CaseAccessService caseAccessService,
            CaseFileService caseFileService,
            @Value("${app.ml-mode:mock}") String executionMode
    ) {
        this.caseRepository = caseRepository;
        this.artifactRepository = artifactRepository;
        this.findingRepository = findingRepository;
        this.reportRepository = reportRepository;
        this.inferenceRunRepository = inferenceRunRepository;
        this.storageService = storageService;
        this.auditService = auditService;
        this.caseProcessingService = caseProcessingService;
        this.caseAccessService = caseAccessService;
        this.caseFileService = caseFileService;
        this.defaultExecutionMode = ExecutionMode.from(executionMode);
    }

    @Override
    public CaseDtos.CaseResponse create(String userEmail, CaseDtos.CreateCaseRequest request) {
        User user = caseAccessService.findUser(userEmail);
        CaseEntity caseEntity = new CaseEntity();
        caseEntity.setPatientPseudoId(request.patientPseudoId());
        caseEntity.setModality(request.modality());
        caseEntity.setStatus(CaseStatus.CREATED);
        caseEntity.setOrigin(CaseOrigin.LIVE_PROCESSED);
        caseEntity.setCreatedBy(user);
        caseEntity.setCreatedAt(Instant.now());
        caseEntity.setUpdatedAt(Instant.now());
        CaseEntity saved = caseRepository.save(caseEntity);
        auditService.log(user.getId(), saved.getId(), AuditAction.CASE_CREATED);
        return map(saved, null);
    }

    @Override
    public List<CaseDtos.CaseResponse> list(String userEmail, CaseStatus status) {
        String normalizedEmail = EmailNormalizer.normalize(userEmail);
        List<CaseEntity> cases = status == null
                ? caseRepository.findByCreatedByEmail(normalizedEmail)
                : caseRepository.findByCreatedByEmailAndStatus(normalizedEmail, status);
        Map<Long, InferenceRun> latestRuns = latestRunsByCaseId(cases);
        return cases.stream()
                .map(caseEntity -> map(caseEntity, latestRuns.get(caseEntity.getId())))
                .toList();
    }

    @Override
    public CaseDtos.CaseResponse get(String userEmail, Long id) {
        CaseEntity caseEntity = caseAccessService.findOwnedCase(userEmail, id);
        InferenceRun latestRun = inferenceRunRepository.findByCaseEntityIdOrderByStartedAtDesc(id).stream().findFirst().orElse(null);
        return map(caseEntity, latestRun);
    }

    @Override
    @Transactional
    public CaseDtos.ArtifactResponse upload(String userEmail, Long id, MultipartFile file) {
        CaseEntity caseEntity = caseAccessService.findOwnedCaseForUpdate(userEmail, id);
        ensureUploadAllowed(caseEntity);
        caseFileService.validateUploadedStudy(file);
        List<Artifact> existingArtifacts = artifactRepository.findByCaseEntityId(id).stream()
                .filter(a -> a.getType() == ArtifactType.ORIGINAL_INPUT || a.getType() == ArtifactType.ORIGINAL_STUDY)
                .toList();
        String objectKey = storageService.saveCaseFile(id, file);
        caseFileService.registerDeleteOnRollback(objectKey);
        existingArtifacts.forEach(artifactRepository::delete);
        caseFileService.registerDeleteAfterCommit(existingArtifacts.stream().map(Artifact::getObjectKey).toList());

        caseEntity.setStatus(CaseStatus.UPLOADED);
        caseEntity.setUpdatedAt(Instant.now());

        Artifact artifact = new Artifact();
        artifact.setCaseEntity(caseEntity);
        artifact.setType(ArtifactType.ORIGINAL_STUDY);
        artifact.setObjectKey(objectKey);
        artifact.setMimeType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        artifact.setOriginalFileName(safeFileName(file.getOriginalFilename()));
        artifact.setStorageDisposition(ArtifactStorageDisposition.MANAGED);
        artifact.setCreatedAt(Instant.now());
        Artifact saved = artifactRepository.save(artifact);
        auditService.log(caseEntity.getCreatedBy().getId(), id, AuditAction.CASE_UPLOADED);
        return toArtifactResponse(saved);
    }

    @Override
    @Transactional
    public void process(String userEmail, Long id) {
        CaseEntity caseEntity = caseAccessService.findOwnedCaseForUpdate(userEmail, id);
        ensureProcessAllowed(caseEntity);
        findOriginalInput(id);
        caseEntity.setStatus(CaseStatus.PROCESSING);
        caseEntity.setUpdatedAt(Instant.now());
        auditService.log(caseEntity.getCreatedBy().getId(), id, AuditAction.INFERENCE_ENQUEUED);
        triggerProcessingAfterCommit(id, defaultExecutionMode);
    }

    @Override
    @Transactional
    public void delete(String userEmail, Long id) {
        CaseEntity caseEntity = caseAccessService.findOwnedCaseForUpdate(userEmail, id);
        ensureDeleteAllowed(caseEntity);
        List<Artifact> artifacts = artifactRepository.findByCaseEntityId(id);
        artifactRepository.deleteByCaseEntityId(id);
        findingRepository.deleteByCaseEntityId(id);
        reportRepository.deleteByCaseEntityId(id);
        inferenceRunRepository.deleteByCaseEntityId(id);
        caseRepository.delete(caseEntity);
        caseFileService.registerDeleteAfterCommit(artifacts.stream().map(Artifact::getObjectKey).toList());
    }

    @Override
    public List<CaseDtos.ArtifactResponse> artifacts(String userEmail, Long id) {
        caseAccessService.findOwnedCase(userEmail, id);
        return artifactRepository.findByCaseEntityId(id).stream().map(this::toArtifactResponse).toList();
    }

    @Override
    public List<CaseDtos.FindingResponse> findings(String userEmail, Long id) {
        caseAccessService.findOwnedCase(userEmail, id);
        return findingRepository.findByCaseEntityId(id).stream()
                .map(f -> new CaseDtos.FindingResponse(f.getId(), f.getType(), f.getLabel(), f.getConfidence(), f.getSizeMm(), f.getVolumeMm3(), f.getLocation()))
                .toList();
    }

    @Override
    public CaseDtos.ReportResponse report(String userEmail, Long id) {
        caseAccessService.findOwnedCase(userEmail, id);
        Report report = reportRepository.findByCaseEntityId(id).orElseThrow(() -> new NotFoundException("Report not found"));
        return new CaseDtos.ReportResponse(report.getReportText(), report.getReportData());
    }

    @Override
    public CaseDtos.StatusResponse status(String userEmail, Long id) {
        CaseEntity caseEntity = caseAccessService.findOwnedCase(userEmail, id);
        InferenceRun run = inferenceRunRepository.findByCaseEntityIdOrderByStartedAtDesc(id).stream().findFirst().orElse(null);
        List<CaseDtos.StageAuditEvent> stages = auditService.listByCase(id).stream()
                .map(a -> new CaseDtos.StageAuditEvent(a.getAction(), a.getCreatedAt(), a.getDetails()))
                .toList();
        return new CaseDtos.StatusResponse(
                id,
                caseEntity.getStatus(),
                run == null ? null : run.getStatus(),
                run == null ? null : run.getExecutionMode(),
                run == null ? null : run.getModelVersion(),
                run == null ? null : run.getMetrics(),
                run == null ? null : run.getFailureDetails(),
                stages
        );
    }

    @Override
    public CaseDtos.Viewer3DResponse viewer3d(String userEmail, Long id) {
        caseAccessService.findOwnedCase(userEmail, id);
        List<Artifact> artifacts = artifactRepository.findByCaseEntityId(id);
        Long liver = artifacts.stream().filter(a -> a.getType() == ArtifactType.LIVER_MESH).map(Artifact::getId).findFirst().orElse(null);
        Long lesion = artifacts.stream().filter(a -> a.getType() == ArtifactType.LESION_MESH).map(Artifact::getId).findFirst().orElse(null);
        return new CaseDtos.Viewer3DResponse(liver, lesion);
    }

    Artifact findOriginalInput(Long id) {
        return artifactRepository.findByCaseEntityId(id).stream()
                .filter(a -> a.getType() == ArtifactType.ORIGINAL_INPUT || a.getType() == ArtifactType.ORIGINAL_STUDY)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Input file is required before processing"));
    }

    private void ensureUploadAllowed(CaseEntity caseEntity) {
        if (!(caseEntity.getStatus() == CaseStatus.CREATED || caseEntity.getStatus() == CaseStatus.UPLOADED || caseEntity.getStatus() == CaseStatus.FAILED)) {
            throw new ConflictException("Upload is not allowed for case status " + caseEntity.getStatus());
        }
    }

    private void ensureProcessAllowed(CaseEntity caseEntity) {
        if (caseEntity.getOrigin() == CaseOrigin.SEEDED_DEMO) {
            throw new ConflictException("Processing is disabled for seeded demo cases");
        }
        if (!(caseEntity.getStatus() == CaseStatus.UPLOADED || caseEntity.getStatus() == CaseStatus.COMPLETED || caseEntity.getStatus() == CaseStatus.FAILED)) {
            throw new ConflictException("Processing is not allowed for case status " + caseEntity.getStatus());
        }
    }

    private void ensureDeleteAllowed(CaseEntity caseEntity) {
        if (caseEntity.getStatus() == CaseStatus.PROCESSING) {
            throw new ConflictException("Delete is not allowed while case is processing");
        }
    }

    private void triggerProcessingAfterCommit(Long caseId, ExecutionMode requestedExecutionMode) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            caseProcessingService.processAsync(caseId, requestedExecutionMode);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                caseProcessingService.processAsync(caseId, requestedExecutionMode);
            }
        });
    }

    private CaseDtos.CaseResponse map(CaseEntity caseEntity, InferenceRun latestRun) {
        CaseOrigin origin = caseEntity.getOrigin() == null ? CaseOrigin.LIVE_PROCESSED : caseEntity.getOrigin();
        return new CaseDtos.CaseResponse(
                caseEntity.getId(),
                caseEntity.getPatientPseudoId(),
                caseEntity.getModality(),
                caseEntity.getStatus(),
                latestRun == null ? null : latestRun.getStatus(),
                latestRun == null ? null : latestRun.getExecutionMode(),
                origin,
                caseEntity.getDemoCategory(),
                caseEntity.getDemoCaseSlug(),
                caseEntity.getDemoManifestVersion(),
                caseEntity.getSourceDataset(),
                caseEntity.getSourceAttribution(),
                caseEntity.getCreatedAt(),
                caseEntity.getUpdatedAt()
        );
    }

    private CaseDtos.ArtifactResponse toArtifactResponse(Artifact artifact) {
        return new CaseDtos.ArtifactResponse(
                artifact.getId(),
                artifact.getType(),
                artifact.getMimeType(),
                artifact.getOriginalFileName(),
                "/api/files/" + artifact.getId() + "/download"
        );
    }

    private Map<Long, InferenceRun> latestRunsByCaseId(List<CaseEntity> cases) {
        if (cases.isEmpty()) {
            return Map.of();
        }
        Map<Long, InferenceRun> runsByCaseId = new LinkedHashMap<>();
        List<InferenceRun> latestRuns = inferenceRunRepository.findLatestByCaseIds(cases.stream().map(CaseEntity::getId).toList());
        if (latestRuns == null) {
            return runsByCaseId;
        }
        latestRuns.forEach(run -> runsByCaseId.put(run.getCaseEntity().getId(), run));
        return runsByCaseId;
    }

    private String safeFileName(String originalFilename) {
        return java.nio.file.Path.of(originalFilename == null ? "upload.bin" : originalFilename)
                .getFileName()
                .toString();
    }
}
