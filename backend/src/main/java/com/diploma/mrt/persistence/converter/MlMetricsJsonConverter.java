package com.diploma.mrt.persistence.converter;

import com.diploma.mrt.model.MlMetrics;

import jakarta.persistence.Converter;

@Converter
public class MlMetricsJsonConverter extends JsonTextConverter<MlMetrics> {
    public MlMetricsJsonConverter() {
        super(MlMetrics.class);
    }
}
