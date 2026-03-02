package com.diploma.mrt.entity;

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
    @Column(nullable = false)
    private String type;
    @Column(nullable = false)
    private String label;
    @Column(nullable = false)
    private Double confidence;
    private Double sizeMm;
    private Double volumeMm3;
    @Column(columnDefinition = "text")
    private String locationJson;
}
