package com.diploma.mrt.service;

import com.diploma.mrt.client.MlClient;
import com.diploma.mrt.dto.CaseDtos;
import com.diploma.mrt.entity.*;
import com.diploma.mrt.repository.*;
import com.diploma.mrt.service.impl.CaseServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CaseServiceFlowTest {
    @Test
    void processAsyncPersistsMlOutputs() {
        CaseRepository caseRepo = mock(CaseRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        ArtifactRepository artifactRepo = mock(ArtifactRepository.class);
        FindingRepository findingRepo = mock(FindingRepository.class);
        ReportRepository reportRepo = mock(ReportRepository.class);
        InferenceRunRepository runRepo = mock(InferenceRunRepository.class);
        StorageService storage = mock(StorageService.class);
        MlClient ml = mock(MlClient.class);
        AuditService audit = mock(AuditService.class);

        CaseEntity c = new CaseEntity();
        c.setId(1L);
        c.setModality(Modality.CT);
        c.setCreatedAt(Instant.now());
        c.setUpdatedAt(Instant.now());
        User u = new User(); u.setId(7L); u.setEmail("u@u"); c.setCreatedBy(u);
        Artifact input = new Artifact();
        input.setType("ORIGINAL_INPUT");
        input.setFilePath("cases/1/input.nii.gz");

        when(caseRepo.findById(1L)).thenReturn(Optional.of(c));
        when(artifactRepo.findByCaseEntityId(1L)).thenReturn(List.of(input));
        when(ml.infer(1L, "CT", "cases/1/input.nii.gz")).thenReturn(new CaseDtos.MlResult(
                "COMPLETED", "real", "{}", "t", "{}", List.of(),
                "a", "l", "les", "lm", "lem"));

        CaseServiceImpl svc = new CaseServiceImpl(caseRepo, userRepo, artifactRepo, findingRepo, reportRepo, runRepo, storage, ml, audit, "mock");
        svc.processAsync(1L);

        verify(artifactRepo, atLeast(3)).save(any(Artifact.class));
        verify(reportRepo).save(any(Report.class));
        verify(runRepo).save(any(InferenceRun.class));
    }
}
