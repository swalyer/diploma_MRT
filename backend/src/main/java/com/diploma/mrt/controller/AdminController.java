package com.diploma.mrt.controller;

import com.diploma.mrt.client.MlClient;
import com.diploma.mrt.demo.importer.DemoCaseImportService;
import com.diploma.mrt.demo.importer.DemoImportResult;
import com.diploma.mrt.demo.manifest.DemoManifest;
import com.diploma.mrt.dto.AdminDtos;
import com.diploma.mrt.dto.MlDtos;
import com.diploma.mrt.entity.ExecutionMode;
import com.diploma.mrt.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final UserRepository userRepository;
    private final MlClient mlClient;
    private final DemoCaseImportService demoCaseImportService;
    private final ExecutionMode executionMode;

    public AdminController(UserRepository userRepository, MlClient mlClient, DemoCaseImportService demoCaseImportService, @Value("${app.ml-mode:mock}") String executionMode) {
        this.userRepository = userRepository;
        this.mlClient = mlClient;
        this.demoCaseImportService = demoCaseImportService;
        this.executionMode = ExecutionMode.from(executionMode);
    }

    @GetMapping("/summary")
    public AdminDtos.AdminSummaryResponse summary(Authentication authentication) {
        MlDtos.MlHealthResponse mlHealth;
        MlDtos.MlCapabilitiesResponse mlCapabilities;
        try {
            mlHealth = mlClient.health();
            mlCapabilities = mlClient.capabilities();
        } catch (Exception exception) {
            mlHealth = new MlDtos.MlHealthResponse("DOWN", executionMode.value(), "unreachable", false, false, true);
            mlCapabilities = new MlDtos.MlCapabilitiesResponse(
                    "v1",
                    "ml-service",
                    "unreachable",
                    java.util.List.of(),
                    java.util.List.of(),
                    java.util.List.of(),
                    java.util.List.of(),
                    false,
                    false,
                    false
            );
        }
        return new AdminDtos.AdminSummaryResponse(
                executionMode,
                authentication.getAuthorities().stream().findFirst().map(authority -> authority.getAuthority()).orElse("ROLE_UNKNOWN"),
                mlHealth,
                mlCapabilities,
                userRepository.findAll().stream()
                        .map(user -> new AdminDtos.AdminUserSummary(user.getId(), user.getEmail(), user.getRole().name()))
                        .toList()
        );
    }

    @PostMapping("/demo-cases/import")
    public DemoImportResult importDemoCase(Authentication authentication, @RequestBody @Valid DemoManifest manifest) {
        return demoCaseImportService.importManifest(authentication.getName(), manifest);
    }
}
