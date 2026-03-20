package com.diploma.mrt.service.impl;

import com.diploma.mrt.dto.CaseDtos;
import com.diploma.mrt.entity.Artifact;
import com.diploma.mrt.entity.ArtifactStorageDisposition;
import com.diploma.mrt.entity.ArtifactType;
import com.diploma.mrt.entity.AuditAction;
import com.diploma.mrt.entity.CaseEntity;
import com.diploma.mrt.entity.CaseOrigin;
import com.diploma.mrt.entity.CaseResultSource;
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
        CaseEntity caseEntity = CaseEntity.newLive(user, request.patientPseudoId(), request.modality(), Instant.now());
        CaseEntity saved = caseRepository.save(caseEntity);
        auditService.log(user.getId(), saved.getId(), AuditAction.CASE_CREATED);
        return map(saved, null);
    }

    @Override
    public List<CaseDtos.CaseResponse> list(String userEmail, CaseStatus status) {
        String normalizedEmail = EmailNormalizer.normalize(userEmail);
        List<CaseEntity> cases = status == null
                ? caseRepository.findReadableByEmailIncludingDemoOrigin(normalizedEmail, CaseOrigin.SEEDED_DEMO)
                : caseRepository.findReadableByEmailIncludingDemoOriginAndStatus(normalizedEmail, CaseOrigin.SEEDED_DEMO, status);
        Map<Long, InferenceRun> latestRuns = latestRunsByCaseId(cases);
        return cases.stream()
                .map(caseEntity -> map(caseEntity, latestRuns.get(caseEntity.getId())))
                .toList();
    }

    @Override
    public CaseDtos.CaseResponse get(String userEmail, Long id) {
        CaseEntity caseEntity = caseAccessService.findReadableCase(userEmail, id);
        InferenceRun latestRun = inferenceRunRepository.findByCaseEntityIdOrderByStartedAtDesc(id).stream().findFirst().orElse(null);
        return map(caseEntity, latestRun);
    }

    @Override
    @Transactional
    public CaseDtos.ArtifactResponse upload(String userEmail, Long id, MultipartFile file) {
        CaseEntity caseEntity = caseAccessService.findMutableCaseForUpdate(userEmail, id);
        caseEntity.assertUploadAllowed();
        caseFileService.validateUploadedStudy(file);
        List<Artifact> existingArtifacts = artifactRepository.findByCaseEntityId(id).stream()
                .filter(Artifact::isSourceStudy)
                .toList();
        String objectKey = storageService.saveCaseFile(id, file);
        caseFileService.registerDeleteOnRollback(objectKey);
        existingArtifacts.forEach(artifactRepository::delete);
        caseFileService.registerDeleteAfterCommit(existingArtifacts.stream().map(Artifact::getObjectKey).toList());

        caseEntity.markUploaded(Instant.now());

        Artifact artifact = new Artifact();
        artifact.setCaseEntity(caseEntity);
        artifact.setType(ArtifactType.canonicalSourceStudy());
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
        CaseEntity caseEntity = caseAccessService.findMutableCaseForUpdate(userEmail, id);
        caseEntity.assertProcessAllowed();
        findOriginalInput(id);
        caseEntity.markProcessing(Instant.now());
        auditService.log(caseEntity.getCreatedBy().getId(), id, AuditAction.INFERENCE_ENQUEUED);
        triggerProcessingAfterCommit(id, defaultExecutionMode);
    }

    @Override
    @Transactional
    public void delete(String userEmail, Long id) {
        CaseEntity caseEntity = caseAccessService.findMutableCaseForUpdate(userEmail, id);
        caseEntity.assertDeleteAllowed();
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
        caseAccessService.findReadableCase(userEmail, id);
        return artifactRepository.findByCaseEntityId(id).stream().map(this::toArtifactResponse).toList();
    }

    @Override
    public List<CaseDtos.FindingResponse> findings(String userEmail, Long id) {
        caseAccessService.findReadableCase(userEmail, id);
        return findingRepository.findByCaseEntityId(id).stream()
                .map(f -> new CaseDtos.FindingResponse(f.getId(), f.getType(), f.getLabel(), f.getConfidence(), f.getSizeMm(), f.getVolumeMm3(), f.getLocation()))
                .toList();
    }

    @Override
    public CaseDtos.ReportResponse report(String userEmail, Long id) {
        caseAccessService.findReadableCase(userEmail, id);
        Report report = reportRepository.findByCaseEntityId(id).orElseThrow(() -> new NotFoundException("Report not found"));
        return new CaseDtos.ReportResponse(report.getReportText(), report.getReportData());
    }

    @Override
    public CaseDtos.StatusResponse status(String userEmail, Long id) {
        CaseEntity caseEntity = caseAccessService.findReadableCase(userEmail, id);
        InferenceRun run = inferenceRunRepository.findByCaseEntityIdOrderByStartedAtDesc(id).stream().findFirst().orElse(null);
        InferenceRun readRun = readableRun(caseEntity, run);
        List<CaseDtos.StageAuditEvent> stages = auditService.listByCase(id).stream()
                .map(a -> new CaseDtos.StageAuditEvent(a.getAction(), a.getCreatedAt(), a.getDetails()))
                .toList();
        return new CaseDtos.StatusResponse(
                id,
                caseEntity.getStatus(),
                readRun == null ? null : readRun.getStatus(),
                readRun == null ? null : readRun.getExecutionMode(),
                readRun == null ? null : readRun.getModelVersion(),
                readRun == null ? null : readRun.getMetrics(),
                readRun == null ? null : readRun.getFailureDetails(),
                isResultReady(caseEntity, run),
                resultSource(caseEntity, run),
                stages
        );
    }

    @Override
    public CaseDtos.Viewer3DResponse viewer3d(String userEmail, Long id) {
        caseAccessService.findReadableCase(userEmail, id);
        List<Artifact> artifacts = artifactRepository.findByCaseEntityId(id);
        Long liver = artifacts.stream().filter(a -> a.getType() == ArtifactType.LIVER_MESH).map(Artifact::getId).findFirst().orElse(null);
        Long lesion = artifacts.stream().filter(a -> a.getType() == ArtifactType.LESION_MESH).map(Artifact::getId).findFirst().orElse(null);
        return new CaseDtos.Viewer3DResponse(liver, lesion);
    }

    Artifact findOriginalInput(Long id) {
        return artifactRepository.findByCaseEntityId(id).stream()
                .filter(Artifact::isSourceStudy)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Input file is required before processing"));
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
        CaseOrigin origin = caseEntity.effectiveOrigin();
        InferenceRun readRun = readableRun(caseEntity, latestRun);
        return new CaseDtos.CaseResponse(
                caseEntity.getId(),
                caseEntity.getPatientPseudoId(),
                caseEntity.getModality(),
                caseEntity.getStatus(),
                readRun == null ? null : readRun.getStatus(),
                readRun == null ? null : readRun.getExecutionMode(),
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

    private InferenceRun readableRun(CaseEntity caseEntity, InferenceRun latestRun) {
        if (caseEntity.effectiveOrigin() == CaseOrigin.SEEDED_DEMO) {
            return null;
        }
        return latestRun;
    }

    private boolean isResultReady(CaseEntity caseEntity, InferenceRun latestRun) {
        if (caseEntity.effectiveOrigin() == CaseOrigin.SEEDED_DEMO) {
            return caseEntity.getStatus() == CaseStatus.COMPLETED;
        }
        return latestRun != null
                && latestRun.getStatus() == com.diploma.mrt.entity.InferenceStatus.COMPLETED
                && caseEntity.getStatus() == CaseStatus.COMPLETED;
    }

    private CaseResultSource resultSource(CaseEntity caseEntity, InferenceRun latestRun) {
        if (caseEntity.effectiveOrigin() == CaseOrigin.SEEDED_DEMO && caseEntity.getStatus() == CaseStatus.COMPLETED) {
            return CaseResultSource.SEEDED_IMPORT;
        }
        if (latestRun != null && latestRun.getStatus() == com.diploma.mrt.entity.InferenceStatus.COMPLETED) {
            return CaseResultSource.ML_INFERENCE;
        }
        return CaseResultSource.NONE;
    }

    private String safeFileName(String originalFilename) {
        return java.nio.file.Path.of(originalFilename == null ? "upload.bin" : originalFilename)
                .getFileName()
                .toString();
    }
}
