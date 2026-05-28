package com.diploma.mrt.integration.ml;

import com.diploma.mrt.entity.InferenceStatus;
import com.diploma.mrt.model.MlMetrics;
import com.diploma.mrt.service.materialization.CaseMaterialization;

import java.util.Objects;

public record MlInferenceResult(
        InferenceStatus status,
        String modelVersion,
        MlMetrics metrics,
        CaseMaterialization materialization
) {
    public MlInferenceResult {
        status = Objects.requireNonNull(status, "status must not be null");
        if (status == InferenceStatus.COMPLETED && materialization == null) {
            throw new IllegalArgumentException("materialization must not be null for completed ML inference results");
        }
    }
}
