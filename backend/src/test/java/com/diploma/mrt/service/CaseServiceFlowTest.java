package com.diploma.mrt.service;

import com.diploma.mrt.client.MlClient;
import com.diploma.mrt.entity.Artifact;
import com.diploma.mrt.entity.ArtifactType;
import com.diploma.mrt.entity.CaseEntity;
import com.diploma.mrt.entity.CaseOrigin;
import com.diploma.mrt.entity.CaseStatus;
import com.diploma.mrt.entity.ExecutionMode;
import com.diploma.mrt.entity.InferenceRun;
import com.diploma.mrt.entity.InferenceStatus;
import com.diploma.mrt.entity.Modality;
import com.diploma.mrt.entity.Report;
import com.diploma.mrt.entity.User;
import com.diploma.mrt.integration.ml.contract.MlContractInferenceRequest;
import com.diploma.mrt.integration.ml.contract.MlContractInferenceResponse;
import com.diploma.mrt.integration.ml.contract.MlContractTypes;
import com.diploma.mrt.integration.ml.mapper.MlContractRequestMapper;
import com.diploma.mrt.integration.ml.mapper.MlContractResponseMapper;
import com.diploma.mrt.repository.ArtifactRepository;
import com.diploma.mrt.repository.CaseRepository;
import com.diploma.mrt.repository.FindingRepository;
import com.diploma.mrt.repository.InferenceRunRepository;
import com.diploma.mrt.repository.ReportRepository;
import com.diploma.mrt.security.JwtService;
import com.diploma.mrt.service.impl.CaseMaterializationService;
import com.diploma.mrt.service.impl.CaseProcessingService;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaseServiceFlowTest {
    @Test
    void processAsyncPersistsMlOutputs() {
        CaseRepository caseRepo = mock(CaseRepository.class);
        ArtifactRepository artifactRepo = mock(ArtifactRepository.class);
        FindingRepository findingRepo = mock(FindingRepository.class);
        ReportRepository reportRepo = mock(ReportRepository.class);
        InferenceRunRepository runRepo = mock(InferenceRunRepository.class);
        StorageService storage = mock(StorageService.class);
        MlClient ml = mock(MlClient.class);
        AuditService audit = mock(AuditService.class);
        PlatformTransactionManager transactionManager = transactionManager();
        AtomicReference<InferenceRun> persistedRun = new AtomicReference<>();
        AtomicReference<Report> persistedReport = new AtomicReference<>();

        CaseEntity c = caseEntity(1L, 7L);
        Artifact input = new Artifact();
        input.setType(ArtifactType.ORIGINAL_STUDY);
        input.setObjectKey("cases/1/input.nii.gz");
        input.setOriginalFileName("input.nii.gz");

        when(caseRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(c));
        when(artifactRepo.findByCaseEntityId(1L)).thenReturn(List.of(input));
        when(runRepo.save(any(InferenceRun.class))).thenAnswer((Answer<InferenceRun>) invocation -> {
            InferenceRun run = invocation.getArgument(0);
            if (run.getId() == null) {
                run.setId(99L);
            }
            persistedRun.set(run);
            return run;
        });
        when(reportRepo.save(any(Report.class))).thenAnswer((Answer<Report>) invocation -> {
            Report report = invocation.getArgument(0);
            persistedReport.set(report);
            return report;
        });
        when(runRepo.findById(any(Long.class))).thenAnswer(invocation -> Optional.ofNullable(persistedRun.get()));
        when(ml.infer(argThat(request -> matchesRequest(request, 1L, 99L, Modality.CT, ExecutionMode.REAL, "cases/1/input.nii.gz"))))
                .thenReturn(successfulResponse("a", "l", "les", "lm", "lem"));

        CaseMaterializationService caseMaterializationService =
                new CaseMaterializationService(artifactRepo, findingRepo, reportRepo, storage);
        CaseProcessingService svc = new CaseProcessingService(
                caseRepo,
                artifactRepo,
                runRepo,
                ml,
                audit,
                transactionManager,
                new MlContractRequestMapper(),
                new MlContractResponseMapper(),
                caseMaterializationService,
                true
        );
        svc.processAsync(1L, ExecutionMode.REAL);

        verify(artifactRepo, atLeastOnce()).save(any(Artifact.class));
        verify(reportRepo).save(any(Report.class));
        verify(runRepo, atLeastOnce()).save(any(InferenceRun.class));
        verify(caseRepo, atLeastOnce()).save(argThat(caseEntity -> caseEntity.getStatus() == CaseStatus.COMPLETED));
        verify(runRepo, atLeastOnce()).save(argThat(run -> run.getExecutionMode() == ExecutionMode.REAL));
        org.junit.jupiter.api.Assertions.assertEquals(
                """
                Findings: f
                
                Impression: i
                
                Limitations: l
                
                Recommendation: r
                """.trim(),
                persistedReport.get().getReportText()
        );
    }

    @Test
    void processAsyncFailsWhenMlReturnsTraversalObjectKey() {
        CaseRepository caseRepo = mock(CaseRepository.class);
        ArtifactRepository artifactRepo = mock(ArtifactRepository.class);
        FindingRepository findingRepo = mock(FindingRepository.class);
        ReportRepository reportRepo = mock(ReportRepository.class);
        InferenceRunRepository runRepo = mock(InferenceRunRepository.class);
        StorageService storage = mock(StorageService.class);
        MlClient ml = mock(MlClient.class);
        AuditService audit = mock(AuditService.class);
        PlatformTransactionManager transactionManager = transactionManager();
        AtomicReference<InferenceRun> persistedRun = new AtomicReference<>();

        CaseEntity c = caseEntity(2L, 8L);
        Artifact input = new Artifact();
        input.setType(ArtifactType.ORIGINAL_STUDY);
        input.setObjectKey("cases/2/input.nii.gz");
        input.setOriginalFileName("input.nii.gz");

        when(caseRepo.findByIdForUpdate(2L)).thenReturn(Optional.of(c));
        when(artifactRepo.findByCaseEntityId(2L)).thenReturn(List.of(input));
        when(runRepo.save(any(InferenceRun.class))).thenAnswer((Answer<InferenceRun>) invocation -> {
            InferenceRun run = invocation.getArgument(0);
            if (run.getId() == null) {
                run.setId(100L);
            }
            persistedRun.set(run);
            return run;
        });
        when(runRepo.findById(any(Long.class))).thenAnswer(invocation -> Optional.ofNullable(persistedRun.get()));
        when(ml.infer(argThat(request -> matchesRequest(request, 2L, 100L, Modality.CT, ExecutionMode.REAL, "cases/2/input.nii.gz"))))
                .thenReturn(successfulResponse("../escape.nii.gz", "l", "les", "lm", "lem"));
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Path escapes storage root"))
                .when(storage).validateObjectKey("../escape.nii.gz");

        CaseMaterializationService caseMaterializationService =
                new CaseMaterializationService(artifactRepo, findingRepo, reportRepo, storage);
        CaseProcessingService svc = new CaseProcessingService(
                caseRepo,
                artifactRepo,
                runRepo,
                ml,
                audit,
                transactionManager,
                new MlContractRequestMapper(),
                new MlContractResponseMapper(),
                caseMaterializationService,
                true
        );
        svc.processAsync(2L, ExecutionMode.REAL);

        verify(storage).validateObjectKey("../escape.nii.gz");
        verify(reportRepo, never()).save(any(Report.class));
        verify(caseRepo, atLeastOnce()).save(argThat(caseEntity -> caseEntity.getStatus() == CaseStatus.FAILED));
        verify(runRepo, atLeastOnce()).save(argThat(run -> run.getStatus() == InferenceStatus.FAILED));
    }

    @Test
    void processAsyncMarksCaseFailedWhenMlReturnsFailedStatus() {
        CaseRepository caseRepo = mock(CaseRepository.class);
        ArtifactRepository artifactRepo = mock(ArtifactRepository.class);
        FindingRepository findingRepo = mock(FindingRepository.class);
        ReportRepository reportRepo = mock(ReportRepository.class);
        InferenceRunRepository runRepo = mock(InferenceRunRepository.class);
        StorageService storage = mock(StorageService.class);
        MlClient ml = mock(MlClient.class);
        AuditService audit = mock(AuditService.class);
        PlatformTransactionManager transactionManager = transactionManager();
        AtomicReference<InferenceRun> persistedRun = new AtomicReference<>();

        CaseEntity c = caseEntity(3L, 9L);
        Artifact input = new Artifact();
        input.setType(ArtifactType.ORIGINAL_STUDY);
        input.setObjectKey("cases/3/input.nii.gz");
        input.setOriginalFileName("input.nii.gz");

        when(caseRepo.findByIdForUpdate(3L)).thenReturn(Optional.of(c));
        when(artifactRepo.findByCaseEntityId(3L)).thenReturn(List.of(input));
        when(runRepo.save(any(InferenceRun.class))).thenAnswer((Answer<InferenceRun>) invocation -> {
            InferenceRun run = invocation.getArgument(0);
            if (run.getId() == null) {
                run.setId(101L);
            }
            persistedRun.set(run);
            return run;
        });
        when(runRepo.findById(any(Long.class))).thenAnswer(invocation -> Optional.ofNullable(persistedRun.get()));
        when(ml.infer(argThat(request -> matchesRequest(request, 3L, 101L, Modality.CT, ExecutionMode.REAL, "cases/3/input.nii.gz"))))
                .thenReturn(new MlContractInferenceResponse(
                        "v1",
                        MlContractTypes.InferenceStatus.FAILED,
                        "real",
                        new MlContractInferenceResponse.Metrics(
                                mapExecutionMode(ExecutionMode.REAL),
                                false,
                                false,
                                false,
                                true
                        ),
                        "t",
                        reportData(),
                        List.of(),
                        new MlContractInferenceResponse.ArtifactOutputs(null, null, null, null, null)
                ));

        CaseMaterializationService caseMaterializationService =
                new CaseMaterializationService(artifactRepo, findingRepo, reportRepo, storage);
        CaseProcessingService svc = new CaseProcessingService(
                caseRepo,
                artifactRepo,
                runRepo,
                ml,
                audit,
                transactionManager,
                new MlContractRequestMapper(),
                new MlContractResponseMapper(),
                caseMaterializationService,
                true
        );
        svc.processAsync(3L, ExecutionMode.REAL);

        verify(reportRepo, never()).save(any(Report.class));
        verify(artifactRepo, never()).save(any(Artifact.class));
        verify(caseRepo, atLeastOnce()).save(argThat(caseEntity -> caseEntity.getStatus() == CaseStatus.FAILED));
        verify(runRepo, atLeastOnce()).save(argThat(run -> run.getStatus() == InferenceStatus.FAILED && run.getExecutionMode() == ExecutionMode.REAL));
    }

    @Test
    void recoverInterruptedProcessingRunsMarksStuckCasesFailed() {
        CaseRepository caseRepo = mock(CaseRepository.class);
        ArtifactRepository artifactRepo = mock(ArtifactRepository.class);
        InferenceRunRepository runRepo = mock(InferenceRunRepository.class);
        MlClient ml = mock(MlClient.class);
        AuditService audit = mock(AuditService.class);
        PlatformTransactionManager transactionManager = transactionManager();

        CaseEntity c = caseEntity(4L, 10L);

        InferenceRun run = new InferenceRun();
        run.setId(404L);
        run.setCaseEntity(c);
        run.setStatus(InferenceStatus.STARTED);
        run.setModelVersion("pipeline");
        run.setStartedAt(Instant.now());

        when(caseRepo.findByStatus(CaseStatus.PROCESSING)).thenReturn(List.of(c));
        when(caseRepo.findByIdForUpdate(4L)).thenReturn(Optional.of(c));
        when(runRepo.findByCaseEntityIdOrderByStartedAtDesc(4L)).thenReturn(List.of(run));

        CaseMaterializationService caseMaterializationService =
                new CaseMaterializationService(artifactRepo, mock(FindingRepository.class), mock(ReportRepository.class), mock(StorageService.class));
        CaseProcessingService svc = new CaseProcessingService(
                caseRepo,
                artifactRepo,
                runRepo,
                ml,
                audit,
                transactionManager,
                new MlContractRequestMapper(),
                new MlContractResponseMapper(),
                caseMaterializationService,
                true
        );
        svc.recoverInterruptedProcessingRuns();

        verify(caseRepo, atLeastOnce()).save(argThat(caseEntity -> caseEntity.getStatus() == CaseStatus.FAILED));
        verify(runRepo).save(argThat(savedRun -> savedRun.getStatus() == InferenceStatus.FAILED && savedRun.getFailureDetails() != null));
    }

    @Test
    void jwtServiceRejectsExamplePlaceholderSecret() {
        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", "generate-a-real-random-secret-before-deploy");

        assertThrows(IllegalStateException.class, () -> ReflectionTestUtils.invokeMethod(jwtService, "validateSecret"));
    }

    private boolean matchesRequest(
            MlContractInferenceRequest request,
            Long caseId,
            Long runId,
            Modality modality,
            ExecutionMode executionMode,
            String inputObjectKey
    ) {
        return request.caseId().equals(caseId)
                && request.requestMetadata().runId().equals(runId)
                && request.modality() == mapModality(modality)
                && request.executionMode() == mapExecutionMode(executionMode)
                && request.fileReferences().inputObjectKey().equals(inputObjectKey);
    }

    private MlContractInferenceResponse successfulResponse(
            String enhancedObjectKey,
            String liverMaskObjectKey,
            String lesionMaskObjectKey,
            String liverMeshObjectKey,
            String lesionMeshObjectKey
    ) {
        return new MlContractInferenceResponse(
                "v1",
                MlContractTypes.InferenceStatus.COMPLETED,
                "real",
                new MlContractInferenceResponse.Metrics(
                        mapExecutionMode(ExecutionMode.REAL),
                        true,
                        true,
                        false,
                        true
                ),
                "t",
                reportData(),
                List.of(),
                new MlContractInferenceResponse.ArtifactOutputs(
                        enhancedObjectKey,
                        liverMaskObjectKey,
                        lesionMaskObjectKey,
                        liverMeshObjectKey,
                        lesionMeshObjectKey
                )
        );
    }

    private MlContractInferenceResponse.ReportData reportData() {
        return new MlContractInferenceResponse.ReportData(
                MlContractTypes.Modality.CT,
                MlContractTypes.ExecutionMode.REAL,
                0,
                true,
                new MlContractInferenceResponse.ReportSections("f", "i", "l", "r"),
                new MlContractInferenceResponse.ReportCapabilities(true, false)
        );
    }

    private MlContractTypes.Modality mapModality(Modality modality) {
        return switch (modality) {
            case CT -> MlContractTypes.Modality.CT;
            case MRI -> MlContractTypes.Modality.MRI;
        };
    }

    private MlContractTypes.ExecutionMode mapExecutionMode(ExecutionMode executionMode) {
        return switch (executionMode) {
            case MOCK -> MlContractTypes.ExecutionMode.MOCK;
            case REAL -> MlContractTypes.ExecutionMode.REAL;
        };
    }

    private CaseEntity caseEntity(Long caseId, Long userId) {
        CaseEntity caseEntity = new CaseEntity();
        caseEntity.setId(caseId);
        caseEntity.setModality(Modality.CT);
        caseEntity.setStatus(CaseStatus.PROCESSING);
        caseEntity.setOrigin(CaseOrigin.LIVE_PROCESSED);
        caseEntity.setCreatedAt(Instant.now());
        caseEntity.setUpdatedAt(Instant.now());
        User user = new User();
        user.setId(userId);
        user.setEmail("u@u");
        caseEntity.setCreatedBy(user);
        return caseEntity;
    }

    private PlatformTransactionManager transactionManager() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        return transactionManager;
    }
}
