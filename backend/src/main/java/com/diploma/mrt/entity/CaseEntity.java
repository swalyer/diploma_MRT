package com.diploma.mrt.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;

import com.diploma.mrt.exception.ConflictException;

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

    public static CaseEntity newLive(User createdBy, String patientPseudoId, Modality modality, Instant now) {
        CaseEntity caseEntity = new CaseEntity();
        caseEntity.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
        caseEntity.patientPseudoId = requirePatientPseudoId(patientPseudoId);
        caseEntity.modality = Objects.requireNonNull(modality, "modality must not be null");
        caseEntity.status = CaseStatus.CREATED;
        caseEntity.origin = CaseOrigin.LIVE_PROCESSED;
        caseEntity.createdAt = requireTimestamp(now);
        caseEntity.updatedAt = now;
        return caseEntity;
    }

    public void applySeededImport(
            User importedBy,
            String patientPseudoId,
            Modality modality,
            DemoCategory demoCategory,
            String demoCaseSlug,
            String demoManifestVersion,
            String sourceDataset,
            String sourceAttribution,
            Instant now
    ) {
        Instant importedAt = requireTimestamp(now);
        if (createdAt == null) {
            createdAt = importedAt;
        }
        if (createdBy == null) {
            createdBy = Objects.requireNonNull(importedBy, "importedBy must not be null");
        }
        this.patientPseudoId = requirePatientPseudoId(patientPseudoId);
        this.modality = Objects.requireNonNull(modality, "modality must not be null");
        this.status = CaseStatus.COMPLETED;
        this.origin = CaseOrigin.SEEDED_DEMO;
        this.demoCategory = demoCategory;
        this.demoCaseSlug = blankToNull(demoCaseSlug);
        this.demoManifestVersion = blankToNull(demoManifestVersion);
        this.sourceDataset = blankToNull(sourceDataset);
        this.sourceAttribution = blankToNull(sourceAttribution);
        this.updatedAt = importedAt;
    }

    public void markUploaded(Instant now) {
        assertUploadAllowed();
        status = CaseStatus.UPLOADED;
        updatedAt = requireTimestamp(now);
    }

    public void assertUploadAllowed() {
        if (!EnumSet.of(CaseStatus.CREATED, CaseStatus.UPLOADED, CaseStatus.FAILED).contains(status)) {
            throw new ConflictException("Upload is not allowed for case status " + status);
        }
    }

    public void markProcessing(Instant now) {
        assertProcessAllowed();
        status = CaseStatus.PROCESSING;
        updatedAt = requireTimestamp(now);
    }

    public void assertProcessAllowed() {
        if (effectiveOrigin() == CaseOrigin.SEEDED_DEMO) {
            throw new ConflictException("Processing is disabled for seeded demo cases");
        }
        if (!EnumSet.of(CaseStatus.UPLOADED, CaseStatus.COMPLETED, CaseStatus.FAILED).contains(status)) {
            throw new ConflictException("Processing is not allowed for case status " + status);
        }
    }

    public void markCompleted(Instant now) {
        status = CaseStatus.COMPLETED;
        updatedAt = requireTimestamp(now);
    }

    public void markFailed(Instant now) {
        status = CaseStatus.FAILED;
        updatedAt = requireTimestamp(now);
    }

    public void assertDeleteAllowed() {
        if (status == CaseStatus.PROCESSING) {
            throw new ConflictException("Delete is not allowed while case is processing");
        }
    }

    public CaseOrigin effectiveOrigin() {
        return origin == null ? CaseOrigin.LIVE_PROCESSED : origin;
    }

    private static String requirePatientPseudoId(String patientPseudoId) {
        if (patientPseudoId == null || patientPseudoId.isBlank()) {
            throw new IllegalArgumentException("patientPseudoId must not be blank");
        }
        return patientPseudoId;
    }

    private static Instant requireTimestamp(Instant now) {
        return Objects.requireNonNull(now, "timestamp must not be null");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
