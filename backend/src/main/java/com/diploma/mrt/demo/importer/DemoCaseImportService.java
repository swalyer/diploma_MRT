package com.diploma.mrt.demo.importer;

import com.diploma.mrt.demo.manifest.DemoManifest;
import com.diploma.mrt.demo.manifest.DemoManifestArtifact;
import com.diploma.mrt.entity.ArtifactType;
import com.diploma.mrt.entity.AuditAction;
import com.diploma.mrt.entity.CaseEntity;
import com.diploma.mrt.entity.CaseOrigin;
import com.diploma.mrt.entity.User;
import com.diploma.mrt.exception.BadRequestException;
import com.diploma.mrt.exception.ConflictException;
import com.diploma.mrt.repository.CaseRepository;
import com.diploma.mrt.repository.InferenceRunRepository;
import com.diploma.mrt.service.AuditService;
import com.diploma.mrt.service.StorageService;
import com.diploma.mrt.service.impl.CaseAccessService;
import com.diploma.mrt.service.impl.CaseMaterializationService;
import com.diploma.mrt.service.materialization.CaseMaterialization;
import com.diploma.mrt.service.materialization.DemoManifestMaterializationMapper;
import com.diploma.mrt.model.ProcessDetails;
import com.diploma.mrt.report.SeededDemoDeterministicReport;
import com.diploma.mrt.report.SeededDemoDeterministicReportBuilder;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DemoCaseImportService {
    private static final List<ArtifactType> REQUIRED_ARTIFACT_TYPES = List.of(
            ArtifactType.LIVER_MASK,
            ArtifactType.LESION_MASK,
            ArtifactType.LIVER_MESH
    );

    private final CaseRepository caseRepository;
    private final InferenceRunRepository inferenceRunRepository;
    private final StorageService storageService;
    private final AuditService auditService;
    private final CaseAccessService caseAccessService;
    private final DemoManifestMaterializationMapper materializationMapper;
    private final CaseMaterializationService caseMaterializationService;
    private final Validator validator;

    public DemoCaseImportService(
            CaseRepository caseRepository,
            InferenceRunRepository inferenceRunRepository,
            StorageService storageService,
            AuditService auditService,
            CaseAccessService caseAccessService,
            DemoManifestMaterializationMapper materializationMapper,
            CaseMaterializationService caseMaterializationService,
            Validator validator
    ) {
        this.caseRepository = caseRepository;
        this.inferenceRunRepository = inferenceRunRepository;
        this.storageService = storageService;
        this.auditService = auditService;
        this.caseAccessService = caseAccessService;
        this.materializationMapper = materializationMapper;
        this.caseMaterializationService = caseMaterializationService;
        this.validator = validator;
    }

    @Transactional
    public DemoImportResult importManifest(String adminEmail, DemoManifest manifest) {
        User adminUser = caseAccessService.findUser(adminEmail);
        validateManifest(manifest);
        validateDeterministicReport(manifest);
        validateArtifacts(manifest);

        Optional<CaseEntity> existingCase = caseRepository.findByDemoCaseSlugAndDemoManifestVersion(
                manifest.caseSlug(),
                manifest.schemaVersion().value()
        );

        DemoImportAction action = existingCase.isPresent() ? DemoImportAction.UPDATED : DemoImportAction.CREATED;
        CaseEntity caseEntity = existingCase.orElseGet(CaseEntity::new);
        Instant importedAt = Instant.now();
        caseEntity.applySeededImport(
                adminUser,
                manifest.patientPseudoId(),
                manifest.modality(),
                manifest.category(),
                manifest.caseSlug(),
                manifest.schemaVersion().value(),
                manifest.sourceDataset(),
                manifest.sourceAttribution(),
                importedAt
        );
        CaseEntity savedCase = caseRepository.save(caseEntity);
        inferenceRunRepository.deleteByCaseEntityId(savedCase.getId());
        CaseMaterialization materialization = materializationMapper.toMaterialization(manifest);
        caseMaterializationService.replace(savedCase, materialization);
        SeededDemoDeterministicReport deterministicReport = SeededDemoDeterministicReportBuilder.build(manifest);

        AuditAction auditAction = action == DemoImportAction.CREATED
                ? AuditAction.DEMO_CASE_IMPORTED
                : AuditAction.DEMO_CASE_UPDATED;
        auditService.log(
                adminUser.getId(),
                savedCase.getId(),
                auditAction,
                new ProcessDetails(
                        "demo_manifest_import",
                        "Imported demo manifest " + manifest.caseSlug() + "@" + manifest.schemaVersion().value(),
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );

        return new DemoImportResult(
                action,
                savedCase.getId(),
                savedCase.getDemoCaseSlug(),
                manifest.schemaVersion(),
                savedCase.getOrigin(),
                manifest.artifacts().size(),
                manifest.findings().size(),
                new DemoImportReport(deterministicReport.manifestReportData(), deterministicReport.reportText())
        );
    }

    private void validateManifest(DemoManifest manifest) {
        List<String> validationErrors = validator.validate(manifest).stream()
                .map(this::formatViolation)
                .sorted()
                .toList();
        if (!validationErrors.isEmpty()) {
            throw new BadRequestException("Demo manifest validation failed: " + String.join("; ", validationErrors));
        }
        if (manifest.origin() != CaseOrigin.SEEDED_DEMO) {
            throw new BadRequestException("Demo manifest origin must be SEEDED_DEMO");
        }
        if (manifest.caseSlug().isBlank()) {
            throw new BadRequestException("Demo manifest caseSlug must not be blank");
        }
    }

    private void validateDeterministicReport(DemoManifest manifest) {
        List<String> mismatches = SeededDemoDeterministicReportBuilder.describeMismatches(manifest);
        if (!mismatches.isEmpty()) {
            throw new BadRequestException(
                    "Demo manifest report must match deterministic seeded report: " + String.join("; ", mismatches)
            );
        }
    }

    private void validateArtifacts(DemoManifest manifest) {
        Map<ArtifactType, DemoManifestArtifact> artifactsByType = new EnumMap<>(ArtifactType.class);
        DemoManifestArtifact sourceStudy = null;

        for (DemoManifestArtifact artifact : manifest.artifacts()) {
            DemoManifestArtifact duplicate = artifactsByType.putIfAbsent(artifact.type(), artifact);
            if (duplicate != null) {
                throw new ConflictException("Duplicate artifact type in manifest: " + artifact.type());
            }
            if (artifact.type().isSourceStudy()) {
                sourceStudy = artifact;
            }
            validateArtifactBinary(artifact);
        }

        if (sourceStudy == null) {
            throw new BadRequestException("Demo manifest must contain ORIGINAL_STUDY artifact");
        }
        for (ArtifactType requiredType : REQUIRED_ARTIFACT_TYPES) {
            if (!artifactsByType.containsKey(requiredType)) {
                throw new BadRequestException("Demo manifest is missing required artifact type " + requiredType);
            }
        }
        if (!manifest.findings().isEmpty() && !artifactsByType.containsKey(ArtifactType.LESION_MESH)) {
            throw new BadRequestException("Demo manifest with findings must contain LESION_MESH artifact");
        }
    }

    private void validateArtifactBinary(DemoManifestArtifact artifact) {
        Resource resource;
        try {
            storageService.validateObjectKey(artifact.objectKey());
            resource = storageService.loadAsResource(artifact.objectKey());
        } catch (RuntimeException exception) {
            throw new BadRequestException("Invalid manifest objectKey " + artifact.objectKey());
        }
        if (!resource.exists() || !resource.isReadable()) {
            throw new BadRequestException("Manifest artifact binary not found for objectKey " + artifact.objectKey());
        }
        try {
            long actualSize = resource.contentLength();
            if (actualSize != artifact.sizeBytes()) {
                throw new BadRequestException("Manifest artifact size mismatch for objectKey " + artifact.objectKey());
            }
            String actualSha256 = sha256(resource);
            if (!actualSha256.equalsIgnoreCase(artifact.sha256())) {
                throw new BadRequestException("Manifest artifact checksum mismatch for objectKey " + artifact.objectKey());
            }
        } catch (IOException exception) {
            throw new BadRequestException("Unable to validate manifest artifact " + artifact.objectKey());
        }
    }

    private String sha256(Resource resource) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = resource.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }

    private String formatViolation(ConstraintViolation<?> violation) {
        return violation.getPropertyPath() + " " + violation.getMessage();
    }
}
