package com.diploma.mrt.dto;

import com.diploma.mrt.entity.Modality;

import java.time.Instant;

/**
 * Longitudinal comparison contract. Comparison is aggregate-level (lesion
 * burden) — it deliberately does not claim registered lesion-to-lesion matching,
 * which would require spatial registration the pipeline does not perform.
 */
public class ComparisonDtos {

    public record StudySummary(
            Long caseId,
            String patientPseudoId,
            Modality modality,
            Instant createdAt,
            int lesionCount,
            double totalVolumeMm3,
            double largestLesionMm
    ) {}

    public record ComparisonDelta(
            int lesionCountDelta,
            double totalVolumeDeltaMm3,
            Double totalVolumePctChange,
            double largestLesionDeltaMm
    ) {}

    public record ComparisonResponse(
            StudySummary baseline,
            StudySummary followup,
            ComparisonDelta delta
    ) {}
}
