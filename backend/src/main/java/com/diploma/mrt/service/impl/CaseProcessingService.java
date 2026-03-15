package com.diploma.mrt.service.impl;

import com.diploma.mrt.client.MlClient;
import com.diploma.mrt.client.contract.MlInferenceRequest;
import com.diploma.mrt.client.contract.MlInferenceResponse;
import com.diploma.mrt.entity.Artifact;
import com.diploma.mrt.entity.ArtifactType;
import com.diploma.mrt.entity.AuditAction;
import com.diploma.mrt.entity.CaseEntity;
import com.diploma.mrt.entity.CaseStatus;
import com.diploma.mrt.entity.ExecutionMode;
import com.diploma.mrt.entity.InferenceRun;
import com.diploma.mrt.entity.InferenceStatus;
import com.diploma.mrt.exception.BadRequestException;
import com.diploma.mrt.exception.NotFoundException;
import com.diploma.mrt.model.ProcessDetails;
import com.diploma.mrt.repository.ArtifactRepository;
import com.diploma.mrt.repository.CaseRepository;
import com.diploma.mrt.repository.InferenceRunRepository;
import com.diploma.mrt.service.AuditService;
import com.diploma.mrt.service.materialization.CaseMaterialization;
import com.diploma.mrt.service.materialization.MlInferenceMaterializationMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.List;

@Service
public class CaseProcessingService {
    private final CaseRepository caseRepository;
    private final ArtifactRepository artifactRepository;
    private final InferenceRunRepository inferenceRunRepository;
    private final MlClient mlClient;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;
    private final boolean processingRecoveryEnabled;
    private final MlInferenceRequestFactory mlInferenceRequestFactory;
    private final MlInferenceMaterializationMapper materializationMapper;
    private final CaseMaterializationService caseMaterializationService;

    public CaseProcessingService(
            CaseRepository caseRepository,
            ArtifactRepository artifactRepository,
            InferenceRunRepository inferenceRunRepository,
            MlClient mlClient,
            AuditService auditService,
            PlatformTransactionManager transactionManager,
            MlInferenceRequestFactory mlInferenceRequestFactory,
            MlInferenceMaterializationMapper materializationMapper,
            CaseMaterializationService caseMaterializationService,
            @Value("${app.processing-recovery-enabled:true}") boolean processingRecoveryEnabled
    ) {
        this.caseRepository = caseRepository;
        this.artifactRepository = artifactRepository;
        this.inferenceRunRepository = inferenceRunRepository;
        this.mlClient = mlClient;
        this.auditService = auditService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.processingRecoveryEnabled = processingRecoveryEnabled;
        this.mlInferenceRequestFactory = mlInferenceRequestFactory;
        this.materializationMapper = materializationMapper;
        this.caseMaterializationService = caseMaterializationService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedProcessingRuns() {
        if (!processingRecoveryEnabled) {
            return;
        }
        for (CaseEntity caseEntity : caseRepository.findByStatus(CaseStatus.PROCESSING)) {
            try {
                transactionTemplate.executeWithoutResult(status -> recoverInterruptedCase(caseEntity.getId()));
            } catch (Exception ignored) {
                // Recovery is best-effort and should not break application startup.
            }
        }
    }

    @Async("caseProcessingExecutor")
    public void processAsync(Long id, ExecutionMode requestedExecutionMode) {
        ProcessingContext context;
        try {
            context = transactionTemplate.execute(status -> openRun(id, requestedExecutionMode));
        } catch (Exception exception) {
            failBeforeRun(id, exception);
            return;
        }
        if (context == null) {
            return;
        }

        try {
            auditService.log(context.userId(), id, AuditAction.INFERENCE_REQUEST_SENT, ProcessDetails.stage("ml_request_sent"));
            MlInferenceRequest request = mlInferenceRequestFactory.build(
                    context.caseId(),
                    context.runId(),
                    context.modality(),
                    context.originalObjectKey(),
                    context.executionMode()
            );
            MlInferenceResponse result = mlClient.infer(request);
            if (result.status() != InferenceStatus.COMPLETED) {
                ProcessDetails failureDetails = buildFailureDetails(result);
                transactionTemplate.executeWithoutResult(status -> markRunFailed(context, failureDetails));
                return;
            }
            transactionTemplate.executeWithoutResult(status -> markRunCompleted(context, result));
        } catch (Exception exception) {
            ProcessDetails failureDetails = buildFailureDetails(exception);
            transactionTemplate.executeWithoutResult(status -> markRunFailed(context, failureDetails));
        }
    }

    private ProcessingContext openRun(Long id, ExecutionMode requestedExecutionMode) {
        CaseEntity caseEntity = caseRepository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Case not found"));
        Artifact original = findOriginalInput(id);
        InferenceRun run = new InferenceRun();
        run.setCaseEntity(caseEntity);
        run.setExecutionMode(requestedExecutionMode);
        run.setStatus(InferenceStatus.STARTED);
        run.setModelVersion("pipeline");
        run.setStartedAt(Instant.now());
        inferenceRunRepository.save(run);
        auditService.log(caseEntity.getCreatedBy().getId(), id, AuditAction.INFERENCE_STARTED);
        return new ProcessingContext(
                caseEntity.getId(),
                run.getId(),
                caseEntity.getCreatedBy().getId(),
                caseEntity.getModality(),
                original.getObjectKey(),
                requestedExecutionMode
        );
    }

    private void markRunCompleted(ProcessingContext context, MlInferenceResponse result) {
        CaseEntity caseEntity = caseRepository.findByIdForUpdate(context.caseId()).orElseThrow(() -> new NotFoundException("Case not found"));
        InferenceRun run = inferenceRunRepository.findById(context.runId()).orElseThrow(() -> new NotFoundException("Inference run not found"));
        CaseMaterialization materialization = materializationMapper.toMaterialization(result);

        caseMaterializationService.replace(caseEntity, materialization);
        run.setExecutionMode(context.executionMode());
        run.setModelVersion(defaultIfBlank(result.modelVersion(), "pipeline"));
        run.setMetrics(result.metrics());
        run.setFailureDetails(null);
        run.setStatus(InferenceStatus.COMPLETED);
        run.setFinishedAt(Instant.now());
        caseEntity.setStatus(CaseStatus.COMPLETED);
        caseEntity.setUpdatedAt(Instant.now());
        inferenceRunRepository.save(run);
        caseRepository.save(caseEntity);
        auditService.log(context.userId(), context.caseId(), AuditAction.INFERENCE_COMPLETED);
    }

    private void markRunFailed(ProcessingContext context, ProcessDetails failureDetails) {
        CaseEntity caseEntity = caseRepository.findByIdForUpdate(context.caseId()).orElseThrow(() -> new NotFoundException("Case not found"));
        InferenceRun run = inferenceRunRepository.findById(context.runId()).orElseThrow(() -> new NotFoundException("Inference run not found"));

        run.setExecutionMode(context.executionMode());
        if (failureDetails != null && failureDetails.modelVersion() != null && !failureDetails.modelVersion().isBlank()) {
            run.setModelVersion(failureDetails.modelVersion());
        }
        run.setStatus(InferenceStatus.FAILED);
        run.setMetrics(null);
        run.setFailureDetails(failureDetails);
        run.setFinishedAt(Instant.now());
        caseEntity.setStatus(CaseStatus.FAILED);
        caseEntity.setUpdatedAt(Instant.now());
        inferenceRunRepository.save(run);
        caseRepository.save(caseEntity);
        auditService.log(context.userId(), context.caseId(), AuditAction.INFERENCE_FAILED, failureDetails);
    }

    private void failBeforeRun(Long caseId, Exception exception) {
        ProcessDetails failureDetails = buildFailureDetails(exception);
        try {
            transactionTemplate.executeWithoutResult(status -> {
                CaseEntity caseEntity = caseRepository.findByIdForUpdate(caseId).orElse(null);
                if (caseEntity == null) {
                    return;
                }
                caseEntity.setStatus(CaseStatus.FAILED);
                caseEntity.setUpdatedAt(Instant.now());
                caseRepository.save(caseEntity);
                auditService.log(caseEntity.getCreatedBy().getId(), caseId, AuditAction.INFERENCE_FAILED, failureDetails);
            });
        } catch (Exception ignored) {
            // No persistent recovery path remains if the case itself cannot be loaded.
        }
    }

    private Artifact findOriginalInput(Long id) {
        return artifactRepository.findByCaseEntityId(id).stream()
                .filter(a -> a.getType() == ArtifactType.ORIGINAL_INPUT || a.getType() == ArtifactType.ORIGINAL_STUDY)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Input file is required before processing"));
    }

    private void recoverInterruptedCase(Long caseId) {
        CaseEntity caseEntity = caseRepository.findByIdForUpdate(caseId).orElse(null);
        if (caseEntity == null || caseEntity.getStatus() != CaseStatus.PROCESSING) {
            return;
        }

        InferenceRun run = inferenceRunRepository.findByCaseEntityIdOrderByStartedAtDesc(caseId).stream().findFirst().orElse(null);
        ProcessDetails failureDetails = new ProcessDetails(
                "backend_restart_recovery",
                "Interrupted processing was marked as failed after backend restart",
                "InterruptedProcessingRecovery",
                null,
                null,
                run == null ? null : run.getModelVersion(),
                null
        );

        if (run != null && run.getStatus() == InferenceStatus.STARTED && run.getFinishedAt() == null) {
            run.setStatus(InferenceStatus.FAILED);
            run.setFinishedAt(Instant.now());
            run.setMetrics(null);
            run.setFailureDetails(failureDetails);
            inferenceRunRepository.save(run);
        }

        caseEntity.setStatus(CaseStatus.FAILED);
        caseEntity.setUpdatedAt(Instant.now());
        caseRepository.save(caseEntity);
        auditService.log(caseEntity.getCreatedBy().getId(), caseId, AuditAction.INFERENCE_RECOVERED_AFTER_RESTART, failureDetails);
    }

    private ProcessDetails buildFailureDetails(Exception exception) {
        if (exception instanceof RestClientResponseException responseException) {
            return new ProcessDetails(
                    "ml_request_validation_failed",
                    "ml-service rejected inference request (" + responseException.getRawStatusCode() + ")",
                    responseException.getClass().getSimpleName(),
                    responseException.getRawStatusCode(),
                    null,
                    null,
                    null
            );
        }
        return new ProcessDetails(
                "inference_failed",
                exception.getMessage() == null ? "unexpected error" : exception.getMessage(),
                exception.getClass().getSimpleName(),
                null,
                null,
                null,
                null
        );
    }

    private ProcessDetails buildFailureDetails(MlInferenceResponse result) {
        return new ProcessDetails(
                "ml_result_failed",
                "ml-service returned non-completed status",
                null,
                null,
                result.status(),
                result.modelVersion(),
                result.metrics()
        );
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record ProcessingContext(
            Long caseId,
            Long runId,
            Long userId,
            com.diploma.mrt.entity.Modality modality,
            String originalObjectKey,
            ExecutionMode executionMode
    ) {}
}
