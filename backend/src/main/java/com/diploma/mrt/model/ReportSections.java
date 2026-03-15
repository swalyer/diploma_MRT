package com.diploma.mrt.model;

public record ReportSections(
        String findings,
        String impression,
        String limitations,
        String recommendation
) {
}
