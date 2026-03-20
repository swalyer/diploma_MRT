package com.diploma.mrt.demo;

import com.diploma.mrt.demo.importer.DemoCaseImportService;
import com.diploma.mrt.demo.importer.DemoImportResult;
import com.diploma.mrt.demo.manifest.DemoManifest;
import com.diploma.mrt.entity.CaseEntity;
import com.diploma.mrt.entity.CaseOrigin;
import com.diploma.mrt.entity.Role;
import com.diploma.mrt.entity.User;
import com.diploma.mrt.repository.ArtifactRepository;
import com.diploma.mrt.repository.CaseRepository;
import com.diploma.mrt.repository.FindingRepository;
import com.diploma.mrt.repository.InferenceRunRepository;
import com.diploma.mrt.repository.ReportRepository;
import com.diploma.mrt.repository.UserRepository;
import com.diploma.mrt.service.AuditService;
import com.diploma.mrt.service.impl.CaseAccessService;
import com.diploma.mrt.service.impl.CaseMaterializationService;
import com.diploma.mrt.service.impl.LocalStorageService;
import com.diploma.mrt.service.materialization.DemoManifestMaterializationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DemoManifestFixturesSmokeTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void importsAllCommittedSeededCtManifestsAgainstRealStorageLayout() throws Exception {
        CaseRepository caseRepository = mock(CaseRepository.class);
        ArtifactRepository artifactRepository = mock(ArtifactRepository.class);
        FindingRepository findingRepository = mock(FindingRepository.class);
        ReportRepository reportRepository = mock(ReportRepository.class);
        InferenceRunRepository inferenceRunRepository = mock(InferenceRunRepository.class);
        AuditService auditService = mock(AuditService.class);
        UserRepository userRepository = mock(UserRepository.class);
        LocalStorageService storageService = new LocalStorageService();

        ReflectionTestUtils.setField(
                storageService,
                "root",
                Path.of("..", "storage").toAbsolutePath().normalize().toString()
        );

        User admin = new User();
        admin.setId(7L);
        admin.setEmail("admin@demo.local");
        admin.setRole(Role.ADMIN);
        when(userRepository.findByEmail("admin@demo.local")).thenReturn(Optional.of(admin));

        when(caseRepository.findByDemoCaseSlugAndDemoManifestVersion(any(), any())).thenReturn(Optional.empty());
        AtomicLong sequence = new AtomicLong(500L);
        when(caseRepository.save(any(CaseEntity.class))).thenAnswer((Answer<CaseEntity>) invocation -> {
            CaseEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(sequence.incrementAndGet());
            }
            return entity;
        });

        DemoCaseImportService service = new DemoCaseImportService(
                caseRepository,
                inferenceRunRepository,
                storageService,
                auditService,
                new CaseAccessService(caseRepository, userRepository),
                new DemoManifestMaterializationMapper(),
                new CaseMaterializationService(artifactRepository, findingRepository, reportRepository, storageService),
                Validation.buildDefaultValidatorFactory().getValidator()
        );

        List<Path> manifests;
        try (var stream = Files.list(Path.of("..", "demo-data", "manifests"))) {
            manifests = stream.filter(path -> path.toString().endsWith(".json")).sorted().toList();
        }

        assertEquals(3, manifests.size());
        for (Path manifestPath : manifests) {
            DemoManifest manifest = objectMapper.readValue(Files.readString(manifestPath), DemoManifest.class);

            DemoImportResult result = service.importManifest("admin@demo.local", manifest);

            assertEquals(CaseOrigin.SEEDED_DEMO, result.origin());
            assertEquals(manifest.caseSlug(), result.caseSlug());
            assertTrue(result.artifactCount() >= 5);
        }
    }
}
