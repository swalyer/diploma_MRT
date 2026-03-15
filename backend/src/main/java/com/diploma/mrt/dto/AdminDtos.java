package com.diploma.mrt.dto;

import com.diploma.mrt.entity.ExecutionMode;

import java.util.List;

public class AdminDtos {
    public record AdminUserSummary(Long id, String email, String role) {}
    public record AdminSummaryResponse(ExecutionMode executionMode, String currentUserRole, MlDtos.MlHealthResponse mlService, MlDtos.MlCapabilitiesResponse mlCapabilities, List<AdminUserSummary> users) {}
}
