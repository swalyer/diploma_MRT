package com.diploma.mrt.service;

import com.diploma.mrt.client.MlClient;
import com.diploma.mrt.config.AppProperties;
import com.diploma.mrt.dto.AdminDtos;
import com.diploma.mrt.dto.MlDtos;
import com.diploma.mrt.entity.CaseOrigin;
import com.diploma.mrt.entity.ExecutionMode;
import com.diploma.mrt.repository.CaseRepository;
import com.diploma.mrt.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminQueryService {
    private static final Logger log = LoggerFactory.getLogger(AdminQueryService.class);

    private final UserRepository userRepository;
    private final CaseRepository caseRepository;
    private final MlClient mlClient;
    private final ExecutionMode executionMode;

    public AdminQueryService(
            UserRepository userRepository,
            CaseRepository caseRepository,
            MlClient mlClient,
            AppProperties appProperties
    ) {
        this.userRepository = userRepository;
        this.caseRepository = caseRepository;
        this.mlClient = mlClient;
        this.executionMode = appProperties.ml().mode();
    }

    public AdminDtos.AdminSummaryResponse buildSummary(String currentUserRole) {
        MlDtos.MlHealthResponse health;
        MlDtos.MlCapabilitiesResponse capabilities;
        try {
            health = mlClient.health();
            capabilities = mlClient.capabilities();
        } catch (Exception exception) {
            log.warn("ml-service summary fetch failed: {}", exception.toString());
            health = unreachableHealth();
            capabilities = unreachableCapabilities();
        }

        List<AdminDtos.AdminUserSummary> users = userRepository.findAll().stream()
                .map(user -> new AdminDtos.AdminUserSummary(user.getId(), user.getEmail(), user.getRole().name()))
                .toList();

        List<AdminDtos.DemoCaseSummary> demoCases = caseRepository.findByOriginOrderByUpdatedAtDesc(CaseOrigin.SEEDED_DEMO).stream()
                .map(caseEntity -> new AdminDtos.DemoCaseSummary(
                        caseEntity.getId(),
                        caseEntity.getDemoCaseSlug(),
                        caseEntity.getDemoManifestVersion(),
                        caseEntity.getPatientPseudoId(),
                        caseEntity.getModality(),
                        caseEntity.getDemoCategory(),
                        caseEntity.getSourceDataset(),
                        caseEntity.getUpdatedAt()
                ))
                .toList();

        return new AdminDtos.AdminSummaryResponse(executionMode, currentUserRole, health, capabilities, users, demoCases);
    }

    private MlDtos.MlHealthResponse unreachableHealth() {
        return new MlDtos.MlHealthResponse("DOWN", executionMode.value(), "unreachable", false, false, true);
    }

    private MlDtos.MlCapabilitiesResponse unreachableCapabilities() {
        return new MlDtos.MlCapabilitiesResponse(
                "v1",
                "ml-service",
                "unreachable",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                false,
                false
        );
    }
}
