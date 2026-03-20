package com.diploma.mrt.service.impl;

import com.diploma.mrt.entity.Artifact;
import com.diploma.mrt.entity.CaseEntity;
import com.diploma.mrt.entity.Finding;
import com.diploma.mrt.entity.Report;
import com.diploma.mrt.repository.ArtifactRepository;
import com.diploma.mrt.repository.FindingRepository;
import com.diploma.mrt.repository.ReportRepository;
import com.diploma.mrt.service.StorageService;
import com.diploma.mrt.service.materialization.CaseMaterialization;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class CaseMaterializationService {
    private final ArtifactRepository artifactRepository;
    private final FindingRepository findingRepository;
    private final ReportRepository reportRepository;
    private final StorageService storageService;

    public CaseMaterializationService(
            ArtifactRepository artifactRepository,
            FindingRepository findingRepository,
            ReportRepository reportRepository,
            StorageService storageService
    ) {
        this.artifactRepository = artifactRepository;
        this.findingRepository = findingRepository;
        this.reportRepository = reportRepository;
        this.storageService = storageService;
    }

    public void replace(CaseEntity caseEntity, CaseMaterialization materialization) {
        List<Artifact> existingArtifacts = artifactRepository.findByCaseEntityId(caseEntity.getId());
        if (existingArtifacts == null) {
            existingArtifacts = List.of();
        }
        List<Artifact> artifactsToReplace = selectArtifactsToReplace(existingArtifacts, materialization.artifactReplaceMode());

        artifactRepository.deleteAll(artifactsToReplace);
        findingRepository.deleteByCaseEntityId(caseEntity.getId());
        reportRepository.deleteByCaseEntityId(caseEntity.getId());
        artifactRepository.flush();
        findingRepository.flush();
        reportRepository.flush();

        Instant materializedAt = Instant.now();
        Set<String> retainedObjectKeys = new LinkedHashSet<>();
        for (CaseMaterialization.ArtifactSpec artifactSpec : materialization.artifacts()) {
            storageService.validateObjectKey(artifactSpec.objectKey());

            Artifact artifact = new Artifact();
            artifact.setCaseEntity(caseEntity);
            artifact.setType(artifactSpec.type());
            artifact.setObjectKey(artifactSpec.objectKey());
            artifact.setOriginalFileName(artifactSpec.originalFileName());
            artifact.setMimeType(artifactSpec.mimeType());
            artifact.setStorageDisposition(artifactSpec.storageDisposition());
            artifact.setCreatedAt(materializedAt);
            artifactRepository.save(artifact);
            retainedObjectKeys.add(artifactSpec.objectKey());
        }

        for (CaseMaterialization.FindingSpec findingSpec : materialization.findings()) {
            Finding finding = new Finding();
            finding.setCaseEntity(caseEntity);
            finding.setType(findingSpec.type());
            finding.setLabel(findingSpec.label());
            finding.setConfidence(findingSpec.confidence());
            finding.setSizeMm(findingSpec.sizeMm());
            finding.setVolumeMm3(findingSpec.volumeMm3());
            finding.setLocation(findingSpec.location());
            findingRepository.save(finding);
        }

        Report report = new Report();
        report.setCaseEntity(caseEntity);
        report.setReportText(materialization.report().reportText());
        report.setReportData(materialization.report().reportData());
        report.setCreatedAt(materializedAt);
        reportRepository.save(report);

        registerManagedObjectCleanup(artifactsToReplace, retainedObjectKeys);
    }

    private List<Artifact> selectArtifactsToReplace(
            List<Artifact> existingArtifacts,
            CaseMaterialization.ArtifactReplaceMode artifactReplaceMode
    ) {
        return switch (artifactReplaceMode) {
            case GENERATED_ONLY -> existingArtifacts.stream()
                    .filter(artifact -> artifact.getType() != null && artifact.getType().isGeneratedOutput())
                    .toList();
            case ALL_CASE_ARTIFACTS -> existingArtifacts;
        };
    }

    private void registerManagedObjectCleanup(List<Artifact> artifactsToReplace, Set<String> retainedObjectKeys) {
        List<String> staleManagedObjectKeys = artifactsToReplace.stream()
                .filter(this::isManagedArtifact)
                .map(Artifact::getObjectKey)
                .filter(objectKey -> !retainedObjectKeys.contains(objectKey))
                .toList();

        if (staleManagedObjectKeys.isEmpty()) {
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            staleManagedObjectKeys.forEach(storageService::delete);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                staleManagedObjectKeys.forEach(storageService::delete);
            }
        });
    }

    private boolean isManagedArtifact(Artifact artifact) {
        return artifact.isManaged();
    }
}
