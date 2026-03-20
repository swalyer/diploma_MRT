package com.diploma.mrt.dto;

import com.diploma.mrt.entity.DemoCategory;
import com.diploma.mrt.entity.ExecutionMode;
import com.diploma.mrt.entity.Modality;

import java.time.Instant;
import java.util.List;

public class AdminDtos {
    public record AdminUserSummary(Long id, String email, String role) {}
    public record DemoCaseSummary(
            Long caseId,
            String caseSlug,
            String manifestVersion,
            String patientPseudoId,
            Modality modality,
            DemoCategory category,
            String sourceDataset,
            Instant updatedAt
    ) {}
    public record AdminSummaryResponse(
            ExecutionMode executionMode,
            String currentUserRole,
            MlDtos.MlHealthResponse mlService,
            MlDtos.MlCapabilitiesResponse mlCapabilities,
            List<AdminUserSummary> users,
            List<DemoCaseSummary> demoCases
    ) {}
}
