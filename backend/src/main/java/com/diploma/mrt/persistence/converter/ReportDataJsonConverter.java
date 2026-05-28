package com.diploma.mrt.persistence.converter;

import com.diploma.mrt.model.ReportData;

import jakarta.persistence.Converter;

@Converter
public class ReportDataJsonConverter extends JsonTextConverter<ReportData> {
    public ReportDataJsonConverter() {
        super(ReportData.class);
    }
}
