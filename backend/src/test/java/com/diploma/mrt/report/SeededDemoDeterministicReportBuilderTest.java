package com.diploma.mrt.report;

import com.diploma.mrt.demo.manifest.DemoManifest;
import com.diploma.mrt.demo.manifest.DemoManifestArtifact;
import com.diploma.mrt.demo.manifest.DemoManifestFinding;
import com.diploma.mrt.demo.manifest.DemoManifestReportData;
import com.diploma.mrt.demo.manifest.DemoManifestSchemaVersion;
import com.diploma.mrt.entity.ArtifactType;
import com.diploma.mrt.entity.CaseOrigin;
import com.diploma.mrt.entity.DemoCategory;
import com.diploma.mrt.entity.FindingType;
import com.diploma.mrt.entity.Modality;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeededDemoDeterministicReportBuilderTest {
    @Test
    void buildsDeterministicReportForNormalCase() {
        SeededDemoDeterministicReport report = SeededDemoDeterministicReportBuilder.build(manifest(List.of()));

        assertEquals(0, report.reportData().lesionCount());
        assertEquals("Structured output contains no lesion components derived from the lesion mask.", report.manifestReportData().findings());
        assertEquals("No lesion components were derived from the seeded artifact masks.", report.manifestReportData().impression());
    }

    @Test
    void buildsDeterministicReportForSingleLesionCase() {
        SeededDemoDeterministicReport report = SeededDemoDeterministicReportBuilder.build(manifest(List.of(lesionFinding("Lesion component #1"))));

        assertEquals(1, report.reportData().lesionCount());
        assertEquals("Structured output contains 1 lesion component(s) derived from the lesion mask.", report.manifestReportData().findings());
        assertEquals("1 lesion component(s) were derived from seeded artifact masks and require clinical correlation.", report.manifestReportData().impression());
        assertEquals(
                """
                Findings: Structured output contains 1 lesion component(s) derived from the lesion mask.
                
                Impression: 1 lesion component(s) were derived from seeded artifact masks and require clinical correlation.
                
                Limitations: Seeded demo import reuses artifact-backed findings and report sections; it does not represent a live ML execution. All outputs remain decision-support only and depend on artifact quality.
                
                Recommendation: Correlate with source images and radiologist review before clinical use.
                """.trim(),
                report.reportText()
        );
    }

    @Test
    void buildsDeterministicReportForMultifocalCase() {
        SeededDemoDeterministicReport report = SeededDemoDeterministicReportBuilder.build(
                manifest(List.of(lesionFinding("Lesion component #1"), lesionFinding("Lesion component #2"), lesionFinding("Lesion component #3")))
        );

        assertEquals(3, report.reportData().lesionCount());
        assertEquals("Structured output contains 3 lesion component(s) derived from the lesion mask.", report.manifestReportData().findings());
        assertEquals("3 lesion component(s) were derived from seeded artifact masks and require clinical correlation.", report.manifestReportData().impression());
        assertEquals(true, report.reportData().evidenceBound());
        assertEquals(true, report.reportData().capabilities().supports3dLiver());
        assertEquals(true, report.reportData().capabilities().supports3dLesion());
    }

    private DemoManifest manifest(List<DemoManifestFinding> findings) {
        return new DemoManifest(
                DemoManifestSchemaVersion.V1,
                "ct-demo-001",
                CaseOrigin.SEEDED_DEMO,
                Modality.CT,
                findings.isEmpty() ? DemoCategory.NORMAL : DemoCategory.MULTIFOCAL,
                "demo-patient",
                "Repository CT smoke fixture",
                "Synthetic attribution",
                List.of(
                        artifact(ArtifactType.ORIGINAL_STUDY),
                        artifact(ArtifactType.LIVER_MASK),
                        artifact(ArtifactType.LESION_MASK),
                        artifact(ArtifactType.LIVER_MESH),
                        artifact(ArtifactType.LESION_MESH)
                ),
                findings,
                new DemoManifestReportData("placeholder", "placeholder", "placeholder", "placeholder"),
                "placeholder"
        );
    }

    private DemoManifestArtifact artifact(ArtifactType type) {
        String fileName = type.name().toLowerCase() + ".bin";
        return new DemoManifestArtifact(type, "demo/" + fileName, fileName, "application/octet-stream", "abc", 1L);
    }

    private DemoManifestFinding lesionFinding(String label) {
        return new DemoManifestFinding(FindingType.LESION, label, null, null, null, null);
    }
}
