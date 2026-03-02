package com.diploma.mrt.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
public class InferenceRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "case_id")
    private CaseEntity caseEntity;
    @Column(nullable = false)
    private String modelVersion;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InferenceStatus status;
    @Column(nullable = false)
    private Instant startedAt;
    private Instant finishedAt;
    @Column(columnDefinition = "text")
    private String metricsJson;
}
