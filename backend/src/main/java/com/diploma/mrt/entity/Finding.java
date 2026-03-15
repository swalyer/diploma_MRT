package com.diploma.mrt.entity;

import com.diploma.mrt.model.FindingLocation;
import com.diploma.mrt.persistence.converter.FindingLocationJsonConverter;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Finding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "case_id")
    private CaseEntity caseEntity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FindingType type;
    @Column(nullable = false)
    private String label;
    private Double confidence;
    private Double sizeMm;
    private Double volumeMm3;
    @Convert(converter = FindingLocationJsonConverter.class)
    @Column(columnDefinition = "text")
    private FindingLocation location;
}
