package com.diploma.mrt.service.materialization;

import com.diploma.mrt.demo.manifest.DemoManifest;
import com.diploma.mrt.entity.ArtifactStorageDisposition;
import com.diploma.mrt.report.SeededDemoDeterministicReport;
import com.diploma.mrt.report.SeededDemoDeterministicReportBuilder;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class DemoManifestMaterializationMapper {
    public CaseMaterialization toMaterialization(DemoManifest manifest) {
        Objects.requireNonNull(manifest, "manifest must not be null");
        SeededDemoDeterministicReport report = SeededDemoDeterministicReportBuilder.build(manifest);

        return new CaseMaterialization(
                CaseMaterialization.ArtifactReplaceMode.ALL_CASE_ARTIFACTS,
                manifest.artifacts().stream()
                        .map(artifact -> new CaseMaterialization.ArtifactSpec(
                                artifact.type(),
                                artifact.objectKey(),
                                artifact.fileName(),
                                artifact.mimeType(),
                                ArtifactStorageDisposition.REFERENCED
                        ))
                        .toList(),
                manifest.findings().stream()
                        .map(finding -> new CaseMaterialization.FindingSpec(
                                finding.type(),
                                finding.label(),
                                finding.confidence(),
                                finding.sizeMm(),
                                finding.volumeMm3(),
                                finding.location()
                        ))
                        .toList(),
                new CaseMaterialization.ReportSpec(
                        report.reportText(),
                        report.reportData()
                )
        );
    }
}
