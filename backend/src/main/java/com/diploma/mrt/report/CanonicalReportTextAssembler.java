package com.diploma.mrt.report;

import com.diploma.mrt.model.ReportSections;

import java.util.Objects;

public final class CanonicalReportTextAssembler {
    private CanonicalReportTextAssembler() {
    }

    public static String assemble(ReportSections sections) {
        ReportSections canonicalSections = Objects.requireNonNull(sections, "sections must not be null");
        return String.join(
                "\n\n",
                "Findings: " + requireSection(canonicalSections.findings(), "findings"),
                "Impression: " + requireSection(canonicalSections.impression(), "impression"),
                "Limitations: " + requireSection(canonicalSections.limitations(), "limitations"),
                "Recommendation: " + requireSection(canonicalSections.recommendation(), "recommendation")
        );
    }

    private static String requireSection(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("report section " + fieldName + " must not be blank");
        }
        return value;
    }
}
