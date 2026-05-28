package com.diploma.mrt.report;

import com.diploma.mrt.demo.manifest.DemoManifest;
import com.diploma.mrt.demo.manifest.DemoManifestReportData;
import com.diploma.mrt.entity.ArtifactType;
import com.diploma.mrt.entity.FindingType;
import com.diploma.mrt.entity.Modality;
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
                buildFindingsSection(source.modality(), lesionCount),
                buildImpressionSection(source.modality(), lesionCount),
                buildLimitationsSection(source.modality()),
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
        return new SeededDemoDeterministicReport(manifestReportData, reportData);
    }

    public static List<String> describeMismatches(DemoManifest manifest) {
        SeededDemoDeterministicReport expected = build(manifest);
        DemoManifestReportData expectedData = expected.manifestReportData();
        DemoManifestReportData actualData = manifest.reportData();
        List<String> mismatches = new ArrayList<>();
        compare("reportData.findings", actualData.findings(), expectedData.findings(), mismatches);
        compare("reportData.impression", actualData.impression(), expectedData.impression(), mismatches);
        compare("reportData.limitations", actualData.limitations(), expectedData.limitations(), mismatches);
        compare("reportData.recommendation", actualData.recommendation(), expectedData.recommendation(), mismatches);
        return mismatches;
    }

    private static String buildFindingsSection(Modality modality, int lesionCount) {
        if (modality == Modality.MRI) {
            return lesionCount > 0
                    ? "Structured output contains " + lesionCount + " heuristic suspicious-zone component(s) derived from the lesion mask."
                    : "Structured output contains no heuristic suspicious-zone components derived from the lesion mask.";
        }
        return lesionCount > 0
                ? "Structured output contains " + lesionCount + " lesion component(s) derived from the lesion mask."
                : "Structured output contains no lesion components derived from the lesion mask.";
    }

    private static String buildImpressionSection(Modality modality, int lesionCount) {
        if (modality == Modality.MRI) {
            return lesionCount > 0
                    ? lesionCount + " heuristic suspicious-zone component(s) were derived from seeded artifact masks and require clinical correlation."
                    : "No heuristic suspicious-zone components were derived from the seeded artifact masks.";
        }
        return lesionCount > 0
                ? lesionCount + " lesion component(s) were derived from seeded artifact masks and require clinical correlation."
                : "No lesion components were derived from the seeded artifact masks.";
    }

    private static String buildLimitationsSection(Modality modality) {
        if (modality == Modality.MRI) {
            return "Seeded MRI demo import reuses heuristic-supported artifact findings and report sections; "
                    + "it does not represent a live ML execution. MRI suspicious-zone output remains heuristic-supported "
                    + "in the current scope. All outputs remain decision-support only and depend on artifact quality.";
        }
        return "Seeded demo import reuses artifact-backed findings and report sections; it does not represent a live ML execution. "
                + "All outputs remain decision-support only and depend on artifact quality.";
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
