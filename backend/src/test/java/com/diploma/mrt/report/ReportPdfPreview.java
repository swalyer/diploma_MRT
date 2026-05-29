package com.diploma.mrt.report;

import com.diploma.mrt.dto.CaseDtos;
import com.diploma.mrt.entity.CaseOrigin;
import com.diploma.mrt.entity.CaseResultSource;
import com.diploma.mrt.entity.CaseStatus;
import com.diploma.mrt.entity.ExecutionMode;
import com.diploma.mrt.entity.FindingType;
import com.diploma.mrt.entity.InferenceStatus;
import com.diploma.mrt.entity.Modality;
import com.diploma.mrt.model.MlMetrics;
import com.diploma.mrt.model.ReportData;
import com.diploma.mrt.model.ReportSections;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/** Dev-only: writes a sample report PDF so the layout can be eyeballed. */
public final class ReportPdfPreview {
    public static void main(String[] args) throws Exception {
        var caseResponse = new CaseDtos.CaseResponse(
                1L, "demo-patient-1", Modality.CT, CaseStatus.COMPLETED, InferenceStatus.COMPLETED,
                ExecutionMode.REAL, CaseOrigin.LIVE_PROCESSED, null, null, null,
                "MSD Task03 Liver", "Medical Segmentation Decathlon", Instant.now(), Instant.now());
        var metrics = new MlMetrics(ExecutionMode.REAL, true, true,
                "nnU-Net v2 [Dataset501_AtlasMRILesion]", "cuda", false, true);
        var status = new CaseDtos.StatusResponse(1L, CaseStatus.COMPLETED, InferenceStatus.COMPLETED, ExecutionMode.REAL,
                "real-ts:on-lesion:nnU-Net v2 [Dataset501]@cuda", metrics, null, true, CaseResultSource.ML_INFERENCE, List.of());
        var sections = new ReportSections(
                "Structured output contains 2 lesion component(s) derived from the lesion mask.",
                "2 lesion component(s) were derived from pipeline output and require clinical correlation.",
                "All outputs remain decision-support only and depend on segmentation quality.",
                "Correlate with source images and radiologist review before clinical use.");
        var report = new CaseDtos.ReportResponse("Findings: 2 lesion component(s).",
                new ReportData(Modality.CT, ExecutionMode.REAL, 2, true, sections, null));
        var findings = List.of(
                new CaseDtos.FindingResponse(1L, FindingType.LESION, "Lesion component #1", 0.82, 24.5, 3120.4, null),
                new CaseDtos.FindingResponse(2L, FindingType.LESION, "Lesion component #2", 0.61, 12.1, 540.2, null));

        byte[] pdf = new ReportPdfService().render(caseResponse, report, findings, status);
        Path out = Path.of(args.length > 0 ? args[0] : "report-preview.pdf");
        Files.write(out, pdf);
        System.out.println("Wrote " + out.toAbsolutePath() + " (" + pdf.length + " bytes)");
    }
}
