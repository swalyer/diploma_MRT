package com.diploma.mrt.persistence.converter;

import com.diploma.mrt.model.ProcessDetails;

import jakarta.persistence.Converter;

@Converter
public class ProcessDetailsJsonConverter extends JsonTextConverter<ProcessDetails> {
    public ProcessDetailsJsonConverter() {
        super(ProcessDetails.class);
    }
}
