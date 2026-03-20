package com.diploma.mrt.service;

import com.diploma.mrt.dto.CaseDtos;
import com.diploma.mrt.entity.Artifact;
import com.diploma.mrt.entity.ArtifactType;
import com.diploma.mrt.entity.CaseEntity;
import com.diploma.mrt.entity.CaseOrigin;
import com.diploma.mrt.entity.CaseResultSource;
import com.diploma.mrt.entity.CaseStatus;
import com.diploma.mrt.entity.ExecutionMode;
import com.diploma.mrt.entity.InferenceRun;
import com.diploma.mrt.entity.InferenceStatus;
import com.diploma.mrt.entity.Modality;
import com.diploma.mrt.entity.Role;
import com.diploma.mrt.entity.User;
import com.diploma.mrt.exception.AccessDeniedException;
import com.diploma.mrt.exception.BadRequestException;
import com.diploma.mrt.exception.ConflictException;
import com.diploma.mrt.repository.ArtifactRepository;
import com.diploma.mrt.repository.CaseRepository;
import com.diploma.mrt.repository.FindingRepository;
import com.diploma.mrt.repository.InferenceRunRepository;
import com.diploma.mrt.repository.ReportRepository;
import com.diploma.mrt.repository.UserRepository;
import com.diploma.mrt.service.impl.CaseAccessService;
import com.diploma.mrt.service.impl.CaseProcessingService;
import com.diploma.mrt.service.impl.CaseFileService;
import com.diploma.mrt.service.impl.CaseServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaseServiceStateTest {
    private CaseRepository caseRepository;
    private UserRepository userRepository;
    private ArtifactRepository artifactRepository;
    private FindingRepository findingRepository;
    private ReportRepository reportRepository;
    private InferenceRunRepository inferenceRunRepository;
    private StorageService storageService;
    private AuditService auditService;
    private CaseProcessingService caseProcessingService;

    @BeforeEach
    void setUp() {
        caseRepository = mock(CaseRepository.class);
        userRepository = mock(UserRepository.class);
        artifactRepository = mock(ArtifactRepository.class);
        findingRepository = mock(FindingRepository.class);
        reportRepository = mock(ReportRepository.class);
        inferenceRunRepository = mock(InferenceRunRepository.class);
        storageService = mock(StorageService.class);
        auditService = mock(AuditService.class);
        caseProcessingService = mock(CaseProcessingService.class);

        when(userRepository.findByEmail("doctor@demo.local")).thenReturn(Optional.of(user("doctor@demo.local", Role.DOCTOR)));
        when(userRepository.findByEmail("admin@demo.local")).thenReturn(Optional.of(user("admin@demo.local", Role.ADMIN)));
    }

    @Test
    void uploadIsRejectedWhileProcessing() {
        CaseServiceImpl service = service();
        CaseEntity processingCase = caseEntity(1L, CaseStatus.PROCESSING);
        when(caseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(processingCase));

        MockMultipartFile file = new MockMultipartFile("file", "study.nii.gz", "application/octet-stream", "test".getBytes());

        assertThrows(ConflictException.class, () -> service.upload("doctor@demo.local", 1L, file));
    }

    @Test
    void processIsRejectedBeforeUpload() {
        CaseServiceImpl service = service();
        CaseEntity createdCase = caseEntity(2L, CaseStatus.CREATED);
        when(caseRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(createdCase));

        assertThrows(ConflictException.class, () -> service.process("doctor@demo.local", 2L));
    }

    @Test
    void processIsRejectedForSeededDemoCaseForDoctor() {
        CaseServiceImpl service = service();
        CaseEntity seededCase = caseEntity(8L, CaseStatus.COMPLETED);
        seededCase.setOrigin(CaseOrigin.SEEDED_DEMO);
        when(caseRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(seededCase));

        assertThrows(AccessDeniedException.class, () -> service.process("doctor@demo.local", 8L));
    }

    @Test
    void processIsRejectedForSeededDemoCaseEvenForAdmin() {
        CaseServiceImpl service = service();
        CaseEntity seededCase = caseEntity(9L, CaseStatus.COMPLETED);
        seededCase.setOrigin(CaseOrigin.SEEDED_DEMO);
        seededCase.setCreatedBy(user("owner@demo.local", Role.ADMIN));
        when(caseRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(seededCase));
        when(artifactRepository.findByCaseEntityId(9L)).thenReturn(List.of(sourceArtifact("cases/9/input.nii.gz")));

        assertThrows(ConflictException.class, () -> service.process("admin@demo.local", 9L));
    }

    @Test
    void processSchedulesAsyncExecutionAfterCommit() {
        CaseServiceImpl service = service();
        CaseEntity uploadedCase = caseEntity(5L, CaseStatus.UPLOADED);
        Artifact input = sourceArtifact("cases/5/input.nii.gz");

        when(caseRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(uploadedCase));
        when(artifactRepository.findByCaseEntityId(5L)).thenReturn(List.of(input));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.process("doctor@demo.local", 5L);

            verify(caseProcessingService, never()).processAsync(eq(5L), eq(ExecutionMode.REAL));
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(caseProcessingService).processAsync(5L, ExecutionMode.REAL);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void uploadRejectsEmptyFile() {
        CaseServiceImpl service = service();
        CaseEntity uploadedCase = caseEntity(6L, CaseStatus.CREATED);
        when(caseRepository.findByIdForUpdate(6L)).thenReturn(Optional.of(uploadedCase));

        MockMultipartFile file = new MockMultipartFile("file", "study.nii.gz", "application/octet-stream", new byte[0]);

        assertThrows(BadRequestException.class, () -> service.upload("doctor@demo.local", 6L, file));
    }

    @Test
    void uploadRejectsInvalidNiftiContentEvenWithValidExtension() {
        CaseServiceImpl service = service();
        CaseEntity uploadedCase = caseEntity(7L, CaseStatus.CREATED);
        when(caseRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(uploadedCase));

        MockMultipartFile file = new MockMultipartFile("file", "study.nii.gz", "application/octet-stream", gzip("not-a-nifti".getBytes()));

        assertThrows(BadRequestException.class, () -> service.upload("doctor@demo.local", 7L, file));
    }

    @Test
    void deleteIsRejectedWhileProcessing() {
        CaseServiceImpl service = service();
        CaseEntity processingCase = caseEntity(3L, CaseStatus.PROCESSING);
        when(caseRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(processingCase));

        assertThrows(ConflictException.class, () -> service.delete("doctor@demo.local", 3L));
    }

    @Test
    void listUsesDatabaseFilteringByOwnerAndStatus() {
        CaseServiceImpl service = service();
        CaseEntity uploadedCase = caseEntity(4L, CaseStatus.UPLOADED);
        when(caseRepository.findReadableByEmailIncludingDemoOriginAndStatus("doctor@demo.local", CaseOrigin.SEEDED_DEMO, CaseStatus.UPLOADED)).thenReturn(List.of(uploadedCase));
        when(inferenceRunRepository.findLatestByCaseIds(List.of(4L))).thenReturn(List.of());

        List<CaseDtos.CaseResponse> cases = service.list("doctor@demo.local", CaseStatus.UPLOADED);

        assertEquals(1, cases.size());
        assertEquals(4L, cases.get(0).id());
        verify(caseRepository).findReadableByEmailIncludingDemoOriginAndStatus("doctor@demo.local", CaseOrigin.SEEDED_DEMO, CaseStatus.UPLOADED);
    }

    @Test
    void seededStatusIsResultReadyWithoutInferenceRun() {
        CaseServiceImpl service = service();
        CaseEntity seededCase = caseEntity(12L, CaseStatus.COMPLETED);
        seededCase.setOrigin(CaseOrigin.SEEDED_DEMO);
        when(caseRepository.findById(12L)).thenReturn(Optional.of(seededCase));
        when(inferenceRunRepository.findByCaseEntityIdOrderByStartedAtDesc(12L)).thenReturn(List.of());

        CaseDtos.StatusResponse status = service.status("doctor@demo.local", 12L);

        assertEquals(CaseResultSource.SEEDED_IMPORT, status.resultSource());
        assertEquals(true, status.resultReady());
        assertEquals(null, status.inferenceStatus());
        assertEquals(null, status.executionMode());
        assertEquals(null, status.modelVersion());
    }

    @Test
    void seededListIgnoresLegacySyntheticInferenceRun() {
        CaseServiceImpl service = service();
        CaseEntity seededCase = caseEntity(13L, CaseStatus.COMPLETED);
        seededCase.setOrigin(CaseOrigin.SEEDED_DEMO);
        when(caseRepository.findReadableByEmailIncludingDemoOrigin("doctor@demo.local", CaseOrigin.SEEDED_DEMO)).thenReturn(List.of(seededCase));
        when(inferenceRunRepository.findLatestByCaseIds(List.of(13L))).thenReturn(List.of(legacySeededRun(seededCase)));

        List<CaseDtos.CaseResponse> cases = service.list("doctor@demo.local", null);

        assertEquals(1, cases.size());
        assertEquals(null, cases.get(0).inferenceStatus());
        assertEquals(null, cases.get(0).executionMode());
    }

    private CaseServiceImpl service() {
        return new CaseServiceImpl(
                caseRepository,
                artifactRepository,
                findingRepository,
                reportRepository,
                inferenceRunRepository,
                storageService,
                auditService,
                caseProcessingService,
                new CaseAccessService(caseRepository, userRepository),
                new CaseFileService(storageService),
                "real"
        );
    }

    private CaseEntity caseEntity(Long id, CaseStatus status) {
        User user = user("doctor@demo.local", Role.DOCTOR);

        CaseEntity caseEntity = new CaseEntity();
        caseEntity.setId(id);
        caseEntity.setPatientPseudoId("P-" + id);
        caseEntity.setModality(Modality.CT);
        caseEntity.setStatus(status);
        caseEntity.setOrigin(CaseOrigin.LIVE_PROCESSED);
        caseEntity.setCreatedBy(user);
        caseEntity.setCreatedAt(Instant.now());
        caseEntity.setUpdatedAt(Instant.now());
        return caseEntity;
    }

    private Artifact sourceArtifact(String objectKey) {
        Artifact artifact = new Artifact();
        artifact.setType(ArtifactType.ORIGINAL_STUDY);
        artifact.setObjectKey(objectKey);
        artifact.setOriginalFileName("input.nii.gz");
        return artifact;
    }

    private InferenceRun legacySeededRun(CaseEntity caseEntity) {
        InferenceRun run = new InferenceRun();
        run.setId(1300L);
        run.setCaseEntity(caseEntity);
        run.setStatus(InferenceStatus.COMPLETED);
        run.setModelVersion("seeded-demo:v1");
        run.setStartedAt(Instant.now());
        run.setFinishedAt(Instant.now());
        return run;
    }

    private User user(String email, Role role) {
        User user = new User();
        user.setId(role == Role.ADMIN ? 11L : 10L);
        user.setEmail(email);
        user.setRole(role);
        return user;
    }

    private byte[] gzip(byte[] payload) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
                gzipOutputStream.write(payload);
            }
            return outputStream.toByteArray();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}
