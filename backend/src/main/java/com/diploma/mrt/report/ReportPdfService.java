package com.diploma.mrt.report;

import com.diploma.mrt.dto.CaseDtos;
import com.diploma.mrt.model.MlMetrics;
import com.diploma.mrt.model.ReportData;
import com.diploma.mrt.model.ReportSections;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Renders the deterministic case report (FR-6) into a physician-signable PDF.
 *
 * The PDF is assembled purely from already-materialized backend data — case
 * metadata, the structured report sections, honest execution metrics, and the
 * structured findings — so it carries the same authoritative content the API
 * exposes, suitable for download, archival, and signature.
 */
@Service
public class ReportPdfService {

    private static final Color INK = new Color(0x12, 0x1B, 0x2E);
    private static final Color MUTED = new Color(0x5A, 0x6B, 0x85);
    private static final Color ACCENT = new Color(0x23, 0x58, 0xFF);
    private static final Color RULE = new Color(0xD0, 0xD9, 0xEA);
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneId.of("UTC"));

    public byte[] render(CaseDtos.CaseResponse caseResponse,
                         CaseDtos.ReportResponse report,
                         List<CaseDtos.FindingResponse> findings,
                         CaseDtos.StatusResponse status) {
        Document doc = new Document(PageSize.A4, 48, 48, 54, 54);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            doc.add(title("Liver CT/MRI Decision-Support Report"));
            doc.add(subtitle("Decision-support output. Not a standalone diagnosis. Requires physician review."));
            doc.add(spacer(8));

            doc.add(metadataTable(caseResponse, status));
            doc.add(spacer(10));

            ReportData data = report == null ? null : report.reportData();
            ReportSections sections = data == null ? null : data.sections();
            if (sections != null) {
                doc.add(sectionHeading("Findings"));
                doc.add(body(sections.findings()));
                doc.add(sectionHeading("Impression"));
                doc.add(body(sections.impression()));
                doc.add(sectionHeading("Limitations"));
                doc.add(body(sections.limitations()));
                doc.add(sectionHeading("Recommendation"));
                doc.add(body(sections.recommendation()));
            } else if (report != null && report.reportText() != null) {
                doc.add(sectionHeading("Report"));
                doc.add(body(report.reportText()));
            } else {
                doc.add(body("No structured report is available for this case."));
            }

            doc.add(spacer(8));
            doc.add(sectionHeading("Structured findings"));
            doc.add(findingsTable(findings));

            doc.add(spacer(18));
            doc.add(signatureBlock());
            doc.close();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to render report PDF for case " + caseResponse.id(), ex);
        }
        return out.toByteArray();
    }

    private PdfPTable metadataTable(CaseDtos.CaseResponse c, CaseDtos.StatusResponse status) {
        MlMetrics metrics = status == null ? null : status.metrics();
        String lesionModel;
        if (metrics != null && Boolean.TRUE.equals(metrics.lesionModel())) {
            lesionModel = metrics.lesionModelName() != null ? metrics.lesionModelName() : "real model";
        } else if (metrics != null && Boolean.FALSE.equals(metrics.lesionModel())) {
            lesionModel = "heuristic (no dedicated model)";
        } else {
            lesionModel = "—";
        }

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        try {
            table.setWidths(new int[]{22, 28, 22, 28});
        } catch (Exception ignored) {
            // fixed widths are best-effort
        }
        kv(table, "Case", "#" + c.id());
        kv(table, "Patient pseudo-ID", nullable(c.patientPseudoId()));
        kv(table, "Modality", c.modality() == null ? "—" : c.modality().name());
        kv(table, "Generated", STAMP.format(Instant.now()));
        kv(table, "Execution mode", status == null || status.executionMode() == null ? "—" : status.executionMode().name());
        kv(table, "Pipeline mode", metrics == null || metrics.mode() == null ? "—" : metrics.mode().name());
        kv(table, "Lesion model", lesionModel);
        kv(table, "Compute device", metrics == null || metrics.device() == null ? "—" : metrics.device());
        kv(table, "Model version", status == null ? "—" : nullable(status.modelVersion()));
        kv(table, "Result source", status == null || status.resultSource() == null ? "—" : status.resultSource().name());
        return table;
    }

    private PdfPTable findingsTable(List<CaseDtos.FindingResponse> findings) {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        try {
            table.setWidths(new int[]{34, 16, 18, 16, 16});
        } catch (Exception ignored) {
            // best-effort column sizing
        }
        headerCell(table, "Finding");
        headerCell(table, "Confidence");
        headerCell(table, "Volume (mm³)");
        headerCell(table, "Size (mm)");
        headerCell(table, "Segment");

        if (findings == null || findings.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("No structured findings.", muted(10)));
            empty.setColspan(5);
            empty.setPadding(8);
            empty.setBorderColor(RULE);
            table.addCell(empty);
            return table;
        }
        for (CaseDtos.FindingResponse f : findings) {
            cell(table, f.label() == null ? "—" : f.label());
            cell(table, f.confidence() == null ? "n/a" : Math.round(f.confidence() * 100) + "%");
            cell(table, f.volumeMm3() == null ? "n/a" : trim(f.volumeMm3()));
            cell(table, f.sizeMm() == null ? "n/a" : trim(f.sizeMm()));
            cell(table, f.location() == null || f.location().segment() == null ? "—" : f.location().segment());
        }
        return table;
    }

    private Paragraph signatureBlock() {
        Paragraph p = new Paragraph();
        p.add(new Phrase("Reviewing physician: ", muted(10)));
        p.add(new Phrase("___________________________        ", body(10)));
        p.add(new Phrase("Date: ", muted(10)));
        p.add(new Phrase("____________________", body(10)));
        return p;
    }

    // --- low-level helpers ---

    private void kv(PdfPTable table, String key, String value) {
        PdfPCell k = new PdfPCell(new Phrase(key, muted(9)));
        k.setBorder(0);
        k.setPaddingBottom(4);
        PdfPCell v = new PdfPCell(new Phrase(value, body(10)));
        v.setBorder(0);
        v.setPaddingBottom(4);
        table.addCell(k);
        table.addCell(v);
    }

    private void headerCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
        cell.setBackgroundColor(ACCENT);
        cell.setPadding(6);
        cell.setBorderColor(RULE);
        table.addCell(cell);
    }

    private void cell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, body(10)));
        cell.setPadding(6);
        cell.setBorderColor(RULE);
        table.addCell(cell);
    }

    private Paragraph title(String text) {
        Paragraph p = new Paragraph(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, INK));
        p.setSpacingAfter(2);
        return p;
    }

    private Paragraph subtitle(String text) {
        return new Paragraph(text, muted(9));
    }

    private Paragraph sectionHeading(String text) {
        Paragraph p = new Paragraph(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, ACCENT));
        p.setSpacingBefore(8);
        p.setSpacingAfter(3);
        return p;
    }

    private Paragraph body(String text) {
        Paragraph p = new Paragraph(text == null ? "—" : text, body(10));
        p.setSpacingAfter(2);
        return p;
    }

    private Paragraph spacer(float height) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(height);
        return p;
    }

    private Font body(int size) {
        return FontFactory.getFont(FontFactory.HELVETICA, size, INK);
    }

    private Font muted(int size) {
        return FontFactory.getFont(FontFactory.HELVETICA, size, MUTED);
    }

    private static String nullable(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String trim(Double value) {
        return String.valueOf(Math.round(value * 100.0) / 100.0);
    }
}
