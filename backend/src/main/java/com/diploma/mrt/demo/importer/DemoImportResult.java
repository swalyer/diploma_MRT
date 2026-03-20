package com.diploma.mrt.demo.importer;

import com.diploma.mrt.demo.manifest.DemoManifestSchemaVersion;
import com.diploma.mrt.entity.CaseOrigin;

public record DemoImportResult(
        DemoImportAction action,
        Long caseId,
        String caseSlug,
        DemoManifestSchemaVersion schemaVersion,
        CaseOrigin origin,
        Integer artifactCount,
        Integer findingCount,
        DemoImportReport report
) {
}
