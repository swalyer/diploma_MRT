package com.diploma.mrt.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "analysis_case")
@Getter
@Setter
public class CaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String patientPseudoId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Modality modality;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaseStatus status;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaseOrigin origin;
    private String demoCaseSlug;
    private String demoManifestVersion;
    private String sourceDataset;
    @Column(columnDefinition = "text")
    private String sourceAttribution;
    @Enumerated(EnumType.STRING)
    private DemoCategory demoCategory;
    @ManyToOne(optional = false)
    @JoinColumn(name = "created_by")
    private User createdBy;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
}
