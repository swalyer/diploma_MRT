package com.diploma.mrt.entity;

import com.diploma.mrt.model.MlMetrics;
import com.diploma.mrt.model.ProcessDetails;
import com.diploma.mrt.persistence.converter.ExecutionModeConverter;
import com.diploma.mrt.persistence.converter.MlMetricsJsonConverter;
import com.diploma.mrt.persistence.converter.ProcessDetailsJsonConverter;

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
    @Convert(converter = ExecutionModeConverter.class)
    @Column(name = "execution_mode")
    private ExecutionMode executionMode;
    @Column(nullable = false)
    private String modelVersion;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InferenceStatus status;
    @Column(nullable = false)
    private Instant startedAt;
    private Instant finishedAt;
    @Convert(converter = MlMetricsJsonConverter.class)
    @Column(columnDefinition = "text")
    private MlMetrics metrics;
    @Convert(converter = ProcessDetailsJsonConverter.class)
    @Column(name = "failure_details_json", columnDefinition = "text")
    private ProcessDetails failureDetails;
}
