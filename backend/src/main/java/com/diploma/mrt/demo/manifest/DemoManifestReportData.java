package com.diploma.mrt.demo.manifest;

import com.diploma.mrt.model.ReportSections;
import jakarta.validation.constraints.NotBlank;

public record DemoManifestReportData(
        @NotBlank String findings,
        @NotBlank String impression,
        @NotBlank String limitations,
        @NotBlank String recommendation
) {
    public ReportSections toSections() {
        return new ReportSections(findings, impression, limitations, recommendation);
    }
}
