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
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportPdfServiceTest {

    private final ReportPdfService service = new ReportPdfService();

    @Test
    void rendersNonEmptyPdfForRealCompletedCase() {
        CaseDtos.CaseResponse caseResponse = new CaseDtos.CaseResponse(
                1L, "demo-patient-1", Modality.CT, CaseStatus.COMPLETED, InferenceStatus.COMPLETED,
                ExecutionMode.REAL, CaseOrigin.LIVE_PROCESSED, null, null, null,
                "MSD Task03 Liver", "Medical Segmentation Decathlon", Instant.now(), Instant.now());

        MlMetrics metrics = new MlMetrics(ExecutionMode.REAL, true, true,
                "nnU-Net v2 [Dataset501_AtlasMRILesion]", "cuda", false, true);
        CaseDtos.StatusResponse status = new CaseDtos.StatusResponse(
                1L, CaseStatus.COMPLETED, InferenceStatus.COMPLETED, ExecutionMode.REAL,
                "real-ts:on-lesion:nnU-Net v2@cuda", metrics, null, true, CaseResultSource.ML_INFERENCE, List.of());

        ReportSections sections = new ReportSections(
                "2 lesion component(s).", "Require clinical correlation.",
                "Decision-support only.", "Correlate with source images.");
        CaseDtos.ReportResponse report = new CaseDtos.ReportResponse(
                "Findings: 2 lesion component(s).",
                new ReportData(Modality.CT, ExecutionMode.REAL, 2, true, sections, null));

        List<CaseDtos.FindingResponse> findings = List.of(
                new CaseDtos.FindingResponse(1L, FindingType.LESION, "Lesion component #1", 0.82, 24.5, 3120.4, null),
                new CaseDtos.FindingResponse(2L, FindingType.LESION, "Lesion component #2", 0.61, 12.1, 540.2, null));

        byte[] pdf = service.render(caseResponse, report, findings, status);

        assertTrue(pdf.length > 800, "PDF should have meaningful content");
        assertEquals("%PDF", new String(pdf, 0, 4), "Output should be a PDF document");
    }

    @Test
    void rendersWhenReportAndFindingsAreAbsent() {
        CaseDtos.CaseResponse caseResponse = new CaseDtos.CaseResponse(
                7L, "demo-patient-7", Modality.MRI, CaseStatus.COMPLETED, InferenceStatus.COMPLETED,
                ExecutionMode.REAL, CaseOrigin.LIVE_PROCESSED, null, null, null, null, null,
                Instant.now(), Instant.now());

        byte[] pdf = service.render(caseResponse, null, List.of(), null);

        assertTrue(pdf.length > 800);
        assertEquals("%PDF", new String(pdf, 0, 4));
    }
}
