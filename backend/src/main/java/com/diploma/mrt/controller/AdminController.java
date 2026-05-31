package com.diploma.mrt.controller;

import com.diploma.mrt.demo.importer.DemoCaseImportService;
import com.diploma.mrt.demo.importer.DemoImportResult;
import com.diploma.mrt.demo.manifest.DemoManifest;
import com.diploma.mrt.dto.AdminDtos;
import com.diploma.mrt.service.AdminQueryService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminQueryService adminQueryService;
    private final DemoCaseImportService demoCaseImportService;

    public AdminController(AdminQueryService adminQueryService, DemoCaseImportService demoCaseImportService) {
        this.adminQueryService = adminQueryService;
        this.demoCaseImportService = demoCaseImportService;
    }

    @GetMapping("/summary")
    public AdminDtos.AdminSummaryResponse summary(Authentication authentication) {
        String currentUserRole = authentication.getAuthorities().stream()
                .findFirst()
                .map(authority -> authority.getAuthority())
                .orElse("ROLE_UNKNOWN");
        return adminQueryService.buildSummary(currentUserRole);
    }

    @GetMapping("/cases")
    public java.util.List<AdminDtos.AdminCaseSummary> allCases() {
        // Path is under /api/admin/** which SecurityConfig restricts to ROLE_ADMIN.
        return adminQueryService.allCases();
    }

    @PostMapping("/demo-cases/import")
    public DemoImportResult importDemoCase(Authentication authentication, @RequestBody @Valid DemoManifest manifest) {
        return demoCaseImportService.importManifest(authentication.getName(), manifest);
    }
}
