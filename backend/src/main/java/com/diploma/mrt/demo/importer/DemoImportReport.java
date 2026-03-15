package com.diploma.mrt.demo.importer;

import com.diploma.mrt.demo.manifest.DemoManifestReportData;

public record DemoImportReport(
        DemoManifestReportData reportData,
        String reportText
) {
}
