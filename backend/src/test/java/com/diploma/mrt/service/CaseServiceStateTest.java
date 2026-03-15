package com.diploma.mrt.service;

import com.diploma.mrt.dto.CaseDtos;
import com.diploma.mrt.entity.Artifact;
import com.diploma.mrt.entity.ArtifactType;
import com.diploma.mrt.entity.CaseEntity;
import com.diploma.mrt.entity.CaseOrigin;
import com.diploma.mrt.entity.CaseStatus;
import com.diploma.mrt.entity.ExecutionMode;
import com.diploma.mrt.entity.Modality;
import com.diploma.mrt.entity.User;
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
    void processIsRejectedForSeededDemoCase() {
        CaseServiceImpl service = service();
        CaseEntity seededCase = caseEntity(8L, CaseStatus.COMPLETED);
        seededCase.setOrigin(CaseOrigin.SEEDED_DEMO);
        when(caseRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(seededCase));

        assertThrows(ConflictException.class, () -> service.process("doctor@demo.local", 8L));
    }

    @Test
    void processSchedulesAsyncExecutionAfterCommit() {
        CaseServiceImpl service = service();
        CaseEntity uploadedCase = caseEntity(5L, CaseStatus.UPLOADED);
        Artifact input = new Artifact();
        input.setType(ArtifactType.ORIGINAL_STUDY);
        input.setObjectKey("cases/5/input.nii.gz");
        input.setOriginalFileName("input.nii.gz");

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
        when(caseRepository.findByCreatedByEmailAndStatus("doctor@demo.local", CaseStatus.UPLOADED)).thenReturn(List.of(uploadedCase));
        when(inferenceRunRepository.findLatestByCaseIds(List.of(4L))).thenReturn(List.of());

        List<CaseDtos.CaseResponse> cases = service.list("doctor@demo.local", CaseStatus.UPLOADED);

        assertEquals(1, cases.size());
        assertEquals(4L, cases.get(0).id());
        verify(caseRepository).findByCreatedByEmailAndStatus("doctor@demo.local", CaseStatus.UPLOADED);
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
        User user = new User();
        user.setId(10L);
        user.setEmail("doctor@demo.local");

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
