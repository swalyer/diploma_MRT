package com.diploma.mrt.report;

import com.diploma.mrt.demo.manifest.DemoManifest;
import com.diploma.mrt.demo.manifest.DemoManifestReportData;
import com.diploma.mrt.entity.ArtifactType;
import com.diploma.mrt.entity.FindingType;
import com.diploma.mrt.model.ReportCapabilities;
import com.diploma.mrt.model.ReportData;
import com.diploma.mrt.model.ReportSections;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SeededDemoDeterministicReportBuilder {
    private SeededDemoDeterministicReportBuilder() {
    }

    public static SeededDemoDeterministicReport build(DemoManifest manifest) {
        DemoManifest source = Objects.requireNonNull(manifest, "manifest must not be null");
        int lesionCount = Math.toIntExact(source.findings().stream()
                .filter(finding -> finding.type() == FindingType.LESION)
                .count());

        ReportSections sections = new ReportSections(
                buildFindingsSection(lesionCount),
                buildImpressionSection(lesionCount),
                "Seeded demo import reuses artifact-backed findings and report sections; it does not represent a live ML execution. "
                        + "All outputs remain decision-support only and depend on artifact quality.",
                "Correlate with source images and radiologist review before clinical use."
        );
        DemoManifestReportData manifestReportData = new DemoManifestReportData(
                sections.findings(),
                sections.impression(),
                sections.limitations(),
                sections.recommendation()
        );
        ReportData reportData = new ReportData(
                source.modality(),
                null,
                lesionCount,
                true,
                sections,
                new ReportCapabilities(
                        hasArtifact(source, ArtifactType.LIVER_MESH),
                        hasArtifact(source, ArtifactType.LESION_MESH)
                )
        );
        return new SeededDemoDeterministicReport(
                manifestReportData,
                reportData,
                CanonicalReportTextAssembler.assemble(sections)
        );
    }

    public static List<String> describeMismatches(DemoManifest manifest) {
        SeededDemoDeterministicReport expected = build(manifest);
        List<String> mismatches = new ArrayList<>();
        compare("reportData.findings", manifest.reportData().findings(), expected.manifestReportData().findings(), mismatches);
        compare("reportData.impression", manifest.reportData().impression(), expected.manifestReportData().impression(), mismatches);
        compare("reportData.limitations", manifest.reportData().limitations(), expected.manifestReportData().limitations(), mismatches);
        compare("reportData.recommendation", manifest.reportData().recommendation(), expected.manifestReportData().recommendation(), mismatches);
        compare("reportText", manifest.reportText(), expected.reportText(), mismatches);
        return mismatches;
    }

    private static String buildFindingsSection(int lesionCount) {
        return lesionCount > 0
                ? "Structured output contains " + lesionCount + " lesion component(s) derived from the lesion mask."
                : "Structured output contains no lesion components derived from the lesion mask.";
    }

    private static String buildImpressionSection(int lesionCount) {
        return lesionCount > 0
                ? lesionCount + " lesion component(s) were derived from seeded artifact masks and require clinical correlation."
                : "No lesion components were derived from the seeded artifact masks.";
    }

    private static boolean hasArtifact(DemoManifest manifest, ArtifactType type) {
        return manifest.artifacts().stream().anyMatch(artifact -> artifact.type() == type);
    }

    private static void compare(String fieldName, String actual, String expected, List<String> mismatches) {
        if (!Objects.equals(actual, expected)) {
            mismatches.add(fieldName + " mismatch");
        }
    }
}
