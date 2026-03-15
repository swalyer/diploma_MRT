package com.diploma.mrt.persistence.converter;

import com.diploma.mrt.entity.ExecutionMode;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class ExecutionModeConverter implements AttributeConverter<ExecutionMode, String> {
    @Override
    public String convertToDatabaseColumn(ExecutionMode attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public ExecutionMode convertToEntityAttribute(String dbData) {
        return ExecutionMode.from(dbData);
    }
}
