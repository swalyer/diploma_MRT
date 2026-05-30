package com.diploma.mrt.service;

import com.diploma.mrt.dto.CaseDtos;
import com.diploma.mrt.dto.ComparisonDtos;
import com.diploma.mrt.entity.FindingType;
import com.diploma.mrt.entity.Modality;
import com.diploma.mrt.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ComparisonServiceTest {

    private final CaseService caseService = mock(CaseService.class);
    private final ComparisonService service = new ComparisonService(caseService);

    private CaseDtos.CaseResponse caseResponse(long id) {
        return new CaseDtos.CaseResponse(id, "patient-1", Modality.CT, null, null, null, null, null,
                null, null, null, null, Instant.now(), Instant.now());
    }

    private CaseDtos.FindingResponse lesion(long id, double sizeMm, double volumeMm3) {
        return new CaseDtos.FindingResponse(id, FindingType.LESION, "Lesion #" + id, null, sizeMm, volumeMm3, null);
    }

    @Test
    void computesGrowthDeltasBetweenStudies() {
        when(caseService.get("u", 1L)).thenReturn(caseResponse(1));
        when(caseService.get("u", 2L)).thenReturn(caseResponse(2));
        when(caseService.findings("u", 1L)).thenReturn(List.of(lesion(1, 10, 1000)));
        when(caseService.findings("u", 2L)).thenReturn(List.of(lesion(1, 12, 1500), lesion(2, 8, 500)));

        // path case (2) is the follow-up, compared against baseline (1)
        ComparisonDtos.ComparisonResponse result = service.compare("u", 1L, 2L);

        assertEquals(1, result.baseline().lesionCount());
        assertEquals(2, result.followup().lesionCount());
        assertEquals(1000.0, result.baseline().totalVolumeMm3());
        assertEquals(2000.0, result.followup().totalVolumeMm3());
        assertEquals(1, result.delta().lesionCountDelta());
        assertEquals(1000.0, result.delta().totalVolumeDeltaMm3());
        assertEquals(100.0, result.delta().totalVolumePctChange());
        assertEquals(2.0, result.delta().largestLesionDeltaMm());
    }

    @Test
    void percentChangeIsNullWhenBaselineHasNoBurden() {
        when(caseService.get("u", 1L)).thenReturn(caseResponse(1));
        when(caseService.get("u", 2L)).thenReturn(caseResponse(2));
        when(caseService.findings("u", 1L)).thenReturn(List.of());
        when(caseService.findings("u", 2L)).thenReturn(List.of(lesion(1, 5, 300)));

        ComparisonDtos.ComparisonResponse result = service.compare("u", 1L, 2L);

        assertEquals(0, result.baseline().lesionCount());
        assertEquals(1, result.delta().lesionCountDelta());
        assertNull(result.delta().totalVolumePctChange());
    }

    @Test
    void rejectsComparingAStudyWithItself() {
        assertThrows(BadRequestException.class, () -> service.compare("u", 5L, 5L));
    }
}
