package com.diploma.mrt.model;

import com.diploma.mrt.entity.ExecutionMode;
import com.diploma.mrt.entity.Modality;
import com.fasterxml.jackson.annotation.JsonAlias;

public record ReportData(
        Modality modality,
        @JsonAlias("mode") ExecutionMode executionMode,
        Integer lesionCount,
        Boolean evidenceBound,
        ReportSections sections,
        ReportCapabilities capabilities
) {
}
