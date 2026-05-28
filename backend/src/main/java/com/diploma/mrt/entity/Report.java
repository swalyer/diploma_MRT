package com.diploma.mrt.entity;

import com.diploma.mrt.model.ReportData;
import com.diploma.mrt.persistence.converter.ReportDataJsonConverter;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
public class Report {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(optional = false)
    @JoinColumn(name = "case_id")
    private CaseEntity caseEntity;
    @Column(columnDefinition = "text", nullable = false)
    private String reportText;
    @Convert(converter = ReportDataJsonConverter.class)
    @Column(name = "report_json", columnDefinition = "text", nullable = false)
    private ReportData reportData;
    @Column(nullable = false)
    private Instant createdAt;
}
