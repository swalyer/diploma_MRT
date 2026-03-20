package com.diploma.mrt.report;

import com.diploma.mrt.demo.manifest.DemoManifestReportData;
import com.diploma.mrt.model.ReportData;

public record SeededDemoDeterministicReport(
        DemoManifestReportData manifestReportData,
        ReportData reportData,
        String reportText
) {
}
