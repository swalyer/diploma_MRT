package com.diploma.mrt.service;

import com.diploma.mrt.dto.CaseDtos;
import com.diploma.mrt.dto.ComparisonDtos;
import com.diploma.mrt.entity.CaseStatus;
import com.diploma.mrt.entity.FindingType;
import com.diploma.mrt.exception.BadRequestException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Builds longitudinal (study-over-study) lesion-burden comparisons. All access
 * goes through {@link CaseService}, so ownership/visibility checks are reused
 * and a user can only compare studies they may read.
 */
@Service
public class ComparisonService {
    private final CaseService caseService;

    public ComparisonService(CaseService caseService) {
        this.caseService = caseService;
    }

    /** Other completed studies for the same patient that this case can be compared against. */
    public List<CaseDtos.CaseResponse> candidates(String user, Long caseId) {
        CaseDtos.CaseResponse current = caseService.get(user, caseId);
        return caseService.list(user, null).stream()
                .filter(c -> !c.id().equals(caseId))
                .filter(c -> Objects.equals(c.patientPseudoId(), current.patientPseudoId()))
                .filter(c -> c.status() == CaseStatus.COMPLETED)
                .toList();
    }

    public ComparisonDtos.ComparisonResponse compare(String user, Long baselineId, Long followupId) {
        if (Objects.equals(baselineId, followupId)) {
            throw new BadRequestException("Baseline and follow-up must be different studies");
        }
        ComparisonDtos.StudySummary baseline = summarize(user, baselineId);
        ComparisonDtos.StudySummary followup = summarize(user, followupId);

        Double pct = baseline.totalVolumeMm3() > 0
                ? round((followup.totalVolumeMm3() - baseline.totalVolumeMm3()) / baseline.totalVolumeMm3() * 100.0)
                : null;
        ComparisonDtos.ComparisonDelta delta = new ComparisonDtos.ComparisonDelta(
                followup.lesionCount() - baseline.lesionCount(),
                round(followup.totalVolumeMm3() - baseline.totalVolumeMm3()),
                pct,
                round(followup.largestLesionMm() - baseline.largestLesionMm())
        );
        return new ComparisonDtos.ComparisonResponse(baseline, followup, delta);
    }

    private ComparisonDtos.StudySummary summarize(String user, Long caseId) {
        CaseDtos.CaseResponse c = caseService.get(user, caseId);
        List<CaseDtos.FindingResponse> lesions = caseService.findings(user, caseId).stream()
                .filter(f -> f.type() == FindingType.LESION)
                .toList();
        double totalVolume = lesions.stream().filter(f -> f.volumeMm3() != null).mapToDouble(CaseDtos.FindingResponse::volumeMm3).sum();
        double largest = lesions.stream().filter(f -> f.sizeMm() != null).mapToDouble(CaseDtos.FindingResponse::sizeMm).max().orElse(0.0);
        return new ComparisonDtos.StudySummary(
                c.id(), c.patientPseudoId(), c.modality(), c.createdAt(),
                lesions.size(), round(totalVolume), round(largest)
        );
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
