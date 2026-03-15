package com.diploma.mrt.persistence.converter;

import com.diploma.mrt.model.FindingLocation;

import jakarta.persistence.Converter;

@Converter
public class FindingLocationJsonConverter extends JsonTextConverter<FindingLocation> {
    public FindingLocationJsonConverter() {
        super(FindingLocation.class);
    }
}
