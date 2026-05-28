package com.diploma.mrt.model;

import com.diploma.mrt.entity.ExecutionMode;

public record MlMetrics(
        ExecutionMode mode,
        Boolean liverModel,
        Boolean lesionModel,
        String lesionModelName,
        String device,
        Boolean medsamAvailable,
        Boolean supportsMri3dSuspiciousZone
) {
}
