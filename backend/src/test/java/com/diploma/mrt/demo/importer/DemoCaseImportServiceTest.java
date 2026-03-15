package com.diploma.mrt.demo.importer;

import com.diploma.mrt.demo.manifest.DemoManifest;
import com.diploma.mrt.demo.manifest.DemoManifestArtifact;
import com.diploma.mrt.demo.manifest.DemoManifestFinding;
import com.diploma.mrt.demo.manifest.DemoManifestReportData;
import com.diploma.mrt.demo.manifest.DemoManifestSchemaVersion;
import com.diploma.mrt.entity.Artifact;
import com.diploma.mrt.entity.ArtifactStorageDisposition;
import com.diploma.mrt.entity.ArtifactType;
import com.diploma.mrt.entity.AuditAction;
import com.diploma.mrt.entity.CaseEntity;
import com.diploma.mrt.entity.CaseOrigin;
import com.diploma.mrt.entity.CaseStatus;
import com.diploma.mrt.entity.DemoCategory;
import com.diploma.mrt.entity.Finding;
import com.diploma.mrt.entity.FindingType;
import com.diploma.mrt.entity.Modality;
import com.diploma.mrt.entity.Report;
import com.diploma.mrt.entity.User;
import com.diploma.mrt.exception.BadRequestException;
import com.diploma.mrt.repository.ArtifactRepository;
import com.diploma.mrt.repository.CaseRepository;
import com.diploma.mrt.repository.FindingRepository;
import com.diploma.mrt.repository.InferenceRunRepository;
import com.diploma.mrt.repository.ReportRepository;
import com.diploma.mrt.repository.UserRepository;
import com.diploma.mrt.service.AuditService;
import com.diploma.mrt.service.StorageService;
import com.diploma.mrt.service.impl.CaseAccessService;
import com.diploma.mrt.service.impl.CaseMaterializationService;
import com.diploma.mrt.service.materialization.DemoManifestMaterializationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.springframework.core.io.PathResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemoCaseImportServiceTest {
    @TempDir
    Path tempDir;

    private CaseRepository caseRepository;
    private ArtifactRepository artifactRepository;
    private FindingRepository findingRepository;
    private ReportRepository reportRepository;
    private InferenceRunRepository inferenceRunRepository;
    private StorageService storageService;
    private AuditService auditService;
    private UserRepository userRepository;
    private DemoCaseImportService demoCaseImportService;

    @BeforeEach
    void setUp() {
        caseRepository = mock(CaseRepository.class);
        artifactRepository = mock(ArtifactRepository.class);
        findingRepository = mock(FindingRepository.class);
        reportRepository = mock(ReportRepository.class);
        inferenceRunRepository = mock(InferenceRunRepository.class);
        storageService = mock(StorageService.class);
        auditService = mock(AuditService.class);
        userRepository = mock(UserRepository.class);
        demoCaseImportService = new DemoCaseImportService(
                caseRepository,
                inferenceRunRepository,
                storageService,
                auditService,
                new CaseAccessService(caseRepository, userRepository),
                new DemoManifestMaterializationMapper(),
                new CaseMaterializationService(artifactRepository, findingRepository, reportRepository, storageService)
        );
    }

    @Test
    void importsSeededDemoManifestAndPersistsExistingReadModel() throws Exception {
        DemoManifest manifest = manifest();
        User admin = adminUser();
        when(userRepository.findByEmail("admin@demo.local")).thenReturn(Optional.of(admin));
        when(caseRepository.findByDemoCaseSlugAndDemoManifestVersion("demo-ct-lesion-001", "v1")).thenReturn(Optional.empty());
        when(caseRepository.save(any(CaseEntity.class))).thenAnswer((Answer<CaseEntity>) invocation -> {
            CaseEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(101L);
            }
            return entity;
        });
        stubStorage(manifest);

        DemoImportResult result = demoCaseImportService.importManifest("admin@demo.local", manifest);

        assertEquals(DemoImportAction.CREATED, result.action());
        assertEquals(101L, result.caseId());
        assertEquals(CaseOrigin.SEEDED_DEMO, result.origin());
        verify(artifactRepository).deleteAll(List.of());
        verify(findingRepository).deleteByCaseEntityId(101L);
        verify(reportRepository).deleteByCaseEntityId(101L);
        verify(inferenceRunRepository).deleteByCaseEntityId(101L);
        verify(artifactRepository, atLeastOnce()).save(any(Artifact.class));
        verify(artifactRepository, atLeastOnce()).save(argThat(artifact -> artifact.getStorageDisposition() == ArtifactStorageDisposition.REFERENCED));
        verify(findingRepository).save(any(Finding.class));

        ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(reportCaptor.capture());
        assertNull(reportCaptor.getValue().getReportData().executionMode());
        assertEquals("Deterministic imported report", reportCaptor.getValue().getReportText());

        verify(auditService).log(eq(7L), eq(101L), eq(AuditAction.DEMO_CASE_IMPORTED), any());
    }

    @Test
    void updatesExistingSeededDemoCaseIdempotently() throws Exception {
        DemoManifest manifest = manifest();
        User admin = adminUser();
        CaseEntity existingCase = new CaseEntity();
        existingCase.setId(202L);
        existingCase.setOrigin(CaseOrigin.SEEDED_DEMO);
        existingCase.setCreatedBy(admin);
        existingCase.setCreatedAt(Instant.now());
        when(userRepository.findByEmail("admin@demo.local")).thenReturn(Optional.of(admin));
        when(caseRepository.findByDemoCaseSlugAndDemoManifestVersion("demo-ct-lesion-001", "v1")).thenReturn(Optional.of(existingCase));
        when(caseRepository.save(any(CaseEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubStorage(manifest);

        DemoImportResult result = demoCaseImportService.importManifest("admin@demo.local", manifest);

        assertEquals(DemoImportAction.UPDATED, result.action());
        assertEquals(202L, result.caseId());
        verify(auditService).log(eq(7L), eq(202L), eq(AuditAction.DEMO_CASE_UPDATED), any());
    }

    @Test
    void rejectsManifestWhenArtifactChecksumDoesNotMatch() throws Exception {
        DemoManifest manifest = manifestWithChecksum("deadbeef");
        when(userRepository.findByEmail("admin@demo.local")).thenReturn(Optional.of(adminUser()));
        stubStorage(manifest);

        assertThrows(BadRequestException.class, () -> demoCaseImportService.importManifest("admin@demo.local", manifest));

        verify(caseRepository, never()).save(any(CaseEntity.class));
        verify(reportRepository, never()).save(any(Report.class));
    }

    @Test
    void rejectsManifestWhenRequiredArtifactsAreMissing() throws Exception {
        DemoManifest manifest = manifestWithoutLiverMesh();
        when(userRepository.findByEmail("admin@demo.local")).thenReturn(Optional.of(adminUser()));
        stubStorage(manifest);

        assertThrows(BadRequestException.class, () -> demoCaseImportService.importManifest("admin@demo.local", manifest));

        verify(caseRepository, never()).save(any(CaseEntity.class));
    }

    private void stubStorage(DemoManifest manifest) {
        Map<String, Path> paths = manifest.artifacts().stream()
                .collect(java.util.stream.Collectors.toMap(DemoManifestArtifact::objectKey, artifact -> artifactPath(artifact.fileName())));

        doAnswer(invocation -> {
            String objectKey = invocation.getArgument(0);
            if (!paths.containsKey(objectKey)) {
                throw new IllegalArgumentException("Unknown object key");
            }
            return null;
        }).when(storageService).validateObjectKey(any(String.class));

        when(storageService.loadAsResource(any(String.class))).thenAnswer(invocation -> {
            String objectKey = invocation.getArgument(0);
            return new PathResource(paths.get(objectKey));
        });
    }

    private DemoManifest manifest() throws Exception {
        DemoManifestArtifact original = artifact(ArtifactType.ORIGINAL_STUDY, "demo/cases/demo-ct-lesion-001/input.nii.gz", "input.nii.gz");
        DemoManifestArtifact liverMask = artifact(ArtifactType.LIVER_MASK, "demo/cases/demo-ct-lesion-001/liver_mask.nii.gz", "liver_mask.nii.gz");
        DemoManifestArtifact lesionMask = artifact(ArtifactType.LESION_MASK, "demo/cases/demo-ct-lesion-001/lesion_mask.nii.gz", "lesion_mask.nii.gz");
        DemoManifestArtifact liverMesh = artifact(ArtifactType.LIVER_MESH, "demo/cases/demo-ct-lesion-001/liver.glb", "liver.glb");
        DemoManifestArtifact lesionMesh = artifact(ArtifactType.LESION_MESH, "demo/cases/demo-ct-lesion-001/lesion.glb", "lesion.glb");

        return new DemoManifest(
                DemoManifestSchemaVersion.V1,
                "demo-ct-lesion-001",
                CaseOrigin.SEEDED_DEMO,
                Modality.CT,
                DemoCategory.SINGLE_LESION,
                "demo-ct-lesion-001",
                "MSD Task03 Liver",
                "Synthetic attribution",
                List.of(original, liverMask, lesionMask, liverMesh, lesionMesh),
                List.of(new DemoManifestFinding(FindingType.LESION, "Lesion component", null, 12.4, 810.0, null)),
                new DemoManifestReportData("One lesion component", "Single lesion", "Demo import", "Review source images"),
                "Deterministic imported report"
        );
    }

    private DemoManifest manifestWithChecksum(String checksum) throws Exception {
        DemoManifest manifest = manifest();
        DemoManifestArtifact original = new DemoManifestArtifact(
                manifest.artifacts().get(0).type(),
                manifest.artifacts().get(0).objectKey(),
                manifest.artifacts().get(0).fileName(),
                manifest.artifacts().get(0).mimeType(),
                checksum,
                manifest.artifacts().get(0).sizeBytes()
        );
        return new DemoManifest(
                manifest.schemaVersion(),
                manifest.caseSlug(),
                manifest.origin(),
                manifest.modality(),
                manifest.category(),
                manifest.patientPseudoId(),
                manifest.sourceDataset(),
                manifest.sourceAttribution(),
                List.of(original, manifest.artifacts().get(1), manifest.artifacts().get(2), manifest.artifacts().get(3), manifest.artifacts().get(4)),
                manifest.findings(),
                manifest.reportData(),
                manifest.reportText()
        );
    }

    private DemoManifest manifestWithoutLiverMesh() throws Exception {
        DemoManifest manifest = manifest();
        return new DemoManifest(
                manifest.schemaVersion(),
                manifest.caseSlug(),
                manifest.origin(),
                manifest.modality(),
                manifest.category(),
                manifest.patientPseudoId(),
                manifest.sourceDataset(),
                manifest.sourceAttribution(),
                List.of(manifest.artifacts().get(0), manifest.artifacts().get(1), manifest.artifacts().get(2), manifest.artifacts().get(4)),
                manifest.findings(),
                manifest.reportData(),
                manifest.reportText()
        );
    }

    private DemoManifestArtifact artifact(ArtifactType type, String objectKey, String fileName) throws Exception {
        Path path = artifactPath(fileName);
        byte[] payload = ("artifact:" + fileName).getBytes();
        Files.createDirectories(path.getParent());
        Files.write(path, payload);
        return new DemoManifestArtifact(
                type,
                objectKey,
                fileName,
                fileName.endsWith(".glb") ? "model/gltf-binary" : "application/gzip",
                sha256(path),
                Files.size(path)
        );
    }

    private Path artifactPath(String fileName) {
        return tempDir.resolve(fileName);
    }

    private String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(Files.readAllBytes(path));
        return HexFormat.of().formatHex(digest.digest());
    }

    private User adminUser() {
        User user = new User();
        user.setId(7L);
        user.setEmail("admin@demo.local");
        return user;
    }
}
