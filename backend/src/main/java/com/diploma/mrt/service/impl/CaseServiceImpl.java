package com.diploma.mrt.service.impl;

import com.diploma.mrt.client.MlClient;
import com.diploma.mrt.dto.CaseDtos;
import com.diploma.mrt.entity.*;
import com.diploma.mrt.exception.NotFoundException;
import com.diploma.mrt.repository.*;
import com.diploma.mrt.service.AuditService;
import com.diploma.mrt.service.CaseService;
import com.diploma.mrt.service.StorageService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

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

    public CaseServiceImpl(CaseRepository caseRepository, UserRepository userRepository, ArtifactRepository artifactRepository, FindingRepository findingRepository, ReportRepository reportRepository, InferenceRunRepository inferenceRunRepository, StorageService storageService, MlClient mlClient, AuditService auditService) {
        this.caseRepository = caseRepository;
        this.userRepository = userRepository;
        this.artifactRepository = artifactRepository;
        this.findingRepository = findingRepository;
        this.reportRepository = reportRepository;
        this.inferenceRunRepository = inferenceRunRepository;
        this.storageService = storageService;
        this.mlClient = mlClient;
        this.auditService = auditService;
    }

    public CaseDtos.CaseResponse create(CaseDtos.CreateCaseRequest request) {
        User user = userRepository.findByEmail("admin@demo.local").orElseThrow(() -> new NotFoundException("User not found"));
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

    public List<CaseDtos.CaseResponse> list(CaseStatus status) {
        List<CaseEntity> cases = status == null ? caseRepository.findAll() : caseRepository.findByStatus(status);
        return cases.stream().map(this::map).toList();
    }

    public CaseDtos.CaseResponse get(Long id) {
        return map(findCase(id));
    }

    @Transactional
    public void upload(Long id, MultipartFile file) {
        CaseEntity caseEntity = findCase(id);
        validateFile(file.getOriginalFilename());
        String path = storageService.saveCaseFile(id, file);
        Artifact artifact = new Artifact();
        artifact.setCaseEntity(caseEntity);
        artifact.setType("ORIGINAL_INPUT");
        artifact.setFilePath(path);
        artifact.setMimeType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        artifact.setCreatedAt(Instant.now());
        artifactRepository.save(artifact);
        caseEntity.setStatus(CaseStatus.UPLOADED);
        caseEntity.setUpdatedAt(Instant.now());
        auditService.log(caseEntity.getCreatedBy().getId(), id, "CASE_UPLOADED", "{}");
    }

    public void process(Long id) {
        processAsync(id);
    }

    @Async
    @Transactional
    public void processAsync(Long id) {
        CaseEntity caseEntity = findCase(id);
        caseEntity.setStatus(CaseStatus.PROCESSING);
        InferenceRun run = new InferenceRun();
        run.setCaseEntity(caseEntity);
        run.setStatus(InferenceStatus.STARTED);
        run.setModelVersion("mock-v1");
        run.setStartedAt(Instant.now());
        inferenceRunRepository.save(run);
        try {
            Artifact original = artifactRepository.findByCaseEntityId(id).stream().findFirst().orElseThrow(() -> new NotFoundException("Input missing"));
            CaseDtos.MlResult result = mlClient.infer(id, caseEntity.getModality().name(), original.getFilePath());
            persistMlResult(caseEntity, result);
            run.setStatus(InferenceStatus.COMPLETED);
            caseEntity.setStatus(CaseStatus.COMPLETED);
        } catch (Exception exception) {
            run.setStatus(InferenceStatus.FAILED);
            caseEntity.setStatus(CaseStatus.FAILED);
        }
        run.setFinishedAt(Instant.now());
        caseEntity.setUpdatedAt(Instant.now());
    }

    private void persistMlResult(CaseEntity caseEntity, CaseDtos.MlResult result) {
        addArtifact(caseEntity, "ENHANCED", result.enhancedPath(), "application/octet-stream");
        addArtifact(caseEntity, "LIVER_MASK", result.liverMaskPath(), "application/octet-stream");
        addArtifact(caseEntity, "LESION_MASK", result.lesionMaskPath(), "application/octet-stream");
        addArtifact(caseEntity, "LIVER_MESH", result.liverMeshPath(), "model/gltf+json");
        addArtifact(caseEntity, "LESION_MESH", result.lesionMeshPath(), "model/gltf+json");
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

    private void addArtifact(CaseEntity caseEntity, String type, String path, String mimeType) {
        Artifact artifact = new Artifact();
        artifact.setCaseEntity(caseEntity);
        artifact.setType(type);
        artifact.setFilePath(path);
        artifact.setMimeType(mimeType);
        artifact.setCreatedAt(Instant.now());
        artifactRepository.save(artifact);
    }

    private void validateFile(String filename) {
        if (filename == null || !(filename.endsWith(".nii") || filename.endsWith(".nii.gz") || filename.endsWith(".zip") || filename.endsWith(".dcm"))) {
            throw new RuntimeException("Unsupported file format");
        }
    }

    public void delete(Long id) {
        caseRepository.deleteById(id);
    }

    public List<CaseDtos.ArtifactResponse> artifacts(Long id) {
        return artifactRepository.findByCaseEntityId(id).stream().map(a -> new CaseDtos.ArtifactResponse(a.getId(), a.getType(), a.getFilePath(), a.getMimeType())).toList();
    }

    public List<CaseDtos.FindingResponse> findings(Long id) {
        return findingRepository.findByCaseEntityId(id).stream().map(f -> new CaseDtos.FindingResponse(f.getId(), f.getType(), f.getLabel(), f.getConfidence(), f.getSizeMm(), f.getVolumeMm3(), f.getLocationJson())).toList();
    }

    public CaseDtos.ReportResponse report(Long id) {
        Report report = reportRepository.findByCaseEntityId(id).orElseThrow(() -> new NotFoundException("Report not found"));
        return new CaseDtos.ReportResponse(report.getReportText(), report.getReportJson());
    }

    public CaseDtos.StatusResponse status(Long id) {
        CaseEntity caseEntity = findCase(id);
        return new CaseDtos.StatusResponse(id, caseEntity.getStatus());
    }

    public CaseDtos.Viewer3DResponse viewer3d(Long id) {
        List<Artifact> artifacts = artifactRepository.findByCaseEntityId(id);
        String liver = artifacts.stream().filter(a -> "LIVER_MESH".equals(a.getType())).map(Artifact::getFilePath).findFirst().orElse(null);
        String lesion = artifacts.stream().filter(a -> "LESION_MESH".equals(a.getType())).map(Artifact::getFilePath).findFirst().orElse(null);
        return new CaseDtos.Viewer3DResponse(liver, lesion);
    }

    private CaseEntity findCase(Long id) {
        return caseRepository.findById(id).orElseThrow(() -> new NotFoundException("Case not found"));
    }

    private CaseDtos.CaseResponse map(CaseEntity caseEntity) {
        return new CaseDtos.CaseResponse(caseEntity.getId(), caseEntity.getPatientPseudoId(), caseEntity.getModality(), caseEntity.getStatus(), caseEntity.getCreatedAt(), caseEntity.getUpdatedAt());
    }
}
