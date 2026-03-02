package com.diploma.mrt.entity;

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
    @Column(columnDefinition = "text", nullable = false)
    private String reportJson;
    @Column(nullable = false)
    private Instant createdAt;
}
