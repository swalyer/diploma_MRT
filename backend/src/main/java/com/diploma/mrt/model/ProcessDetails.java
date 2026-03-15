package com.diploma.mrt.model;

import com.diploma.mrt.entity.InferenceStatus;

public record ProcessDetails(
        String stage,
        String message,
        String error,
        Integer httpStatus,
        InferenceStatus mlStatus,
        String modelVersion,
        MlMetrics metrics
) {
    public static ProcessDetails empty() {
        return new ProcessDetails(null, null, null, null, null, null, null);
    }

    public static ProcessDetails stage(String stage) {
        return new ProcessDetails(stage, null, null, null, null, null, null);
    }
}
