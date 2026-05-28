package com.diploma.mrt.demo.manifest;

import com.diploma.mrt.entity.FindingType;
import com.diploma.mrt.model.FindingLocation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DemoManifestFinding(
        @NotNull FindingType type,
        @NotBlank String label,
        Double confidence,
        Double sizeMm,
        Double volumeMm3,
        FindingLocation location
) {
}
