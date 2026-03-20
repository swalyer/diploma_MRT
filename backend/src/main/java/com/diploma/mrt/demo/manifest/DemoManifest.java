package com.diploma.mrt.demo.manifest;

import com.diploma.mrt.entity.CaseOrigin;
import com.diploma.mrt.entity.DemoCategory;
import com.diploma.mrt.entity.Modality;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record DemoManifest(
        @NotNull DemoManifestSchemaVersion schemaVersion,
        @NotBlank String caseSlug,
        @NotNull CaseOrigin origin,
        @NotNull Modality modality,
        @NotNull DemoCategory category,
        @NotBlank String patientPseudoId,
        String sourceDataset,
        String sourceAttribution,
        @NotEmpty List<@Valid DemoManifestArtifact> artifacts,
        @NotNull List<@Valid DemoManifestFinding> findings,
        @NotNull @Valid DemoManifestReportData reportData,
        @NotBlank String reportText
) {
}
