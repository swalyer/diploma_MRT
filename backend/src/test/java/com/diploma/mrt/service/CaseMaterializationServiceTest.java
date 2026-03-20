package com.diploma.mrt.service;

import com.diploma.mrt.entity.Artifact;
import com.diploma.mrt.entity.ArtifactStorageDisposition;
import com.diploma.mrt.entity.ArtifactType;
import com.diploma.mrt.entity.CaseEntity;
import com.diploma.mrt.entity.CaseStatus;
import com.diploma.mrt.entity.FindingType;
import com.diploma.mrt.entity.Modality;
import com.diploma.mrt.entity.User;
import com.diploma.mrt.model.ReportCapabilities;
import com.diploma.mrt.model.ReportData;
import com.diploma.mrt.model.ReportSections;
import com.diploma.mrt.service.impl.CaseMaterializationService;
import com.diploma.mrt.service.materialization.CaseMaterialization;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaseMaterializationServiceTest {
    @Test
    void generatedOnlyReplacesGeneratedArtifactsAndDeletesOnlyManagedBinaries() {
        com.diploma.mrt.repository.ArtifactRepository artifactRepository = mock(com.diploma.mrt.repository.ArtifactRepository.class);
        com.diploma.mrt.repository.FindingRepository findingRepository = mock(com.diploma.mrt.repository.FindingRepository.class);
        com.diploma.mrt.repository.ReportRepository reportRepository = mock(com.diploma.mrt.repository.ReportRepository.class);
        StorageService storageService = mock(StorageService.class);
        CaseMaterializationService service = new CaseMaterializationService(artifactRepository, findingRepository, reportRepository, storageService);

        Artifact input = artifact(ArtifactType.ORIGINAL_STUDY, "cases/11/input.nii.gz", ArtifactStorageDisposition.MANAGED);
        Artifact oldLiverMesh = artifact(ArtifactType.LIVER_MESH, "cases/11/old-liver.glb", ArtifactStorageDisposition.MANAGED);
        Artifact oldLesionMesh = artifact(ArtifactType.LESION_MESH, "demo/11/old-lesion.glb", ArtifactStorageDisposition.REFERENCED);
        when(artifactRepository.findByCaseEntityId(11L)).thenReturn(List.of(input, oldLiverMesh, oldLesionMesh));

        CaseMaterialization materialization = new CaseMaterialization(
                CaseMaterialization.ArtifactReplaceMode.GENERATED_ONLY,
                List.of(new CaseMaterialization.ArtifactSpec(
                        ArtifactType.LIVER_MESH,
                        "cases/11/new-liver.glb",
                        "new-liver.glb",
                        "model/gltf-binary",
                        ArtifactStorageDisposition.MANAGED
                )),
                List.of(new CaseMaterialization.FindingSpec(FindingType.LESION, "Lesion", null, 10.0, 100.0, null)),
                new CaseMaterialization.ReportSpec("report", reportData())
        );

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.replace(caseEntity(11L), materialization);

            ArgumentCaptor<List<Artifact>> deletedArtifacts = ArgumentCaptor.forClass(List.class);
            verify(artifactRepository).deleteAll(deletedArtifacts.capture());
            assertEquals(2, deletedArtifacts.getValue().size());
            assertTrue(deletedArtifacts.getValue().contains(oldLiverMesh));
            assertTrue(deletedArtifacts.getValue().contains(oldLesionMesh));
            assertTrue(!deletedArtifacts.getValue().contains(input));

            verify(storageService, never()).delete("demo/11/old-lesion.glb");
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(storageService).delete("cases/11/old-liver.glb");
            verify(storageService, never()).delete("demo/11/old-lesion.glb");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void allCaseArtifactsReplaceEntireCaseButKeepReferencedBinariesOutOfCleanup() {
        com.diploma.mrt.repository.ArtifactRepository artifactRepository = mock(com.diploma.mrt.repository.ArtifactRepository.class);
        com.diploma.mrt.repository.FindingRepository findingRepository = mock(com.diploma.mrt.repository.FindingRepository.class);
        com.diploma.mrt.repository.ReportRepository reportRepository = mock(com.diploma.mrt.repository.ReportRepository.class);
        StorageService storageService = mock(StorageService.class);
        CaseMaterializationService service = new CaseMaterializationService(artifactRepository, findingRepository, reportRepository, storageService);

        Artifact oldManagedInput = artifact(ArtifactType.ORIGINAL_STUDY, "cases/12/input.nii.gz", ArtifactStorageDisposition.MANAGED);
        Artifact oldReferencedMask = artifact(ArtifactType.LIVER_MASK, "demo/12/liver-mask.nii.gz", ArtifactStorageDisposition.REFERENCED);
        when(artifactRepository.findByCaseEntityId(12L)).thenReturn(List.of(oldManagedInput, oldReferencedMask));

        CaseMaterialization materialization = new CaseMaterialization(
                CaseMaterialization.ArtifactReplaceMode.ALL_CASE_ARTIFACTS,
                List.of(new CaseMaterialization.ArtifactSpec(
                        ArtifactType.ORIGINAL_STUDY,
                        "demo/12/new-input.nii.gz",
                        "new-input.nii.gz",
                        "application/gzip",
                        ArtifactStorageDisposition.REFERENCED
                )),
                List.of(),
                new CaseMaterialization.ReportSpec("report", reportData())
        );

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.replace(caseEntity(12L), materialization);

            ArgumentCaptor<List<Artifact>> deletedArtifacts = ArgumentCaptor.forClass(List.class);
            verify(artifactRepository).deleteAll(deletedArtifacts.capture());
            assertEquals(2, deletedArtifacts.getValue().size());
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(storageService).delete("cases/12/input.nii.gz");
            verify(storageService, never()).delete("demo/12/liver-mask.nii.gz");
            verify(artifactRepository, times(1)).save(any(Artifact.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private Artifact artifact(ArtifactType type, String objectKey, ArtifactStorageDisposition storageDisposition) {
        Artifact artifact = new Artifact();
        artifact.setType(type);
        artifact.setObjectKey(objectKey);
        artifact.setOriginalFileName(objectKey.substring(objectKey.lastIndexOf('/') + 1));
        artifact.setStorageDisposition(storageDisposition);
        return artifact;
    }

    private CaseEntity caseEntity(Long id) {
        User user = new User();
        user.setId(77L);
        user.setEmail("doctor@demo.local");

        CaseEntity caseEntity = new CaseEntity();
        caseEntity.setId(id);
        caseEntity.setPatientPseudoId("P-" + id);
        caseEntity.setModality(Modality.CT);
        caseEntity.setStatus(CaseStatus.COMPLETED);
        caseEntity.setCreatedBy(user);
        caseEntity.setCreatedAt(Instant.now());
        caseEntity.setUpdatedAt(Instant.now());
        return caseEntity;
    }

    private ReportData reportData() {
        return new ReportData(
                Modality.CT,
                null,
                1,
                true,
                new ReportSections("f", "i", "l", "r"),
                new ReportCapabilities(true, true)
        );
    }
}
