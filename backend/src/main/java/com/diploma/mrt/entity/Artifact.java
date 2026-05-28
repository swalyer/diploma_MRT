package com.diploma.mrt.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
public class Artifact {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "case_id")
    private CaseEntity caseEntity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArtifactType type;
    @Column(name = "object_key", columnDefinition = "text", nullable = false)
    private String objectKey;
    @Column(nullable = false)
    private String mimeType;
    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArtifactStorageDisposition storageDisposition = ArtifactStorageDisposition.MANAGED;
    @Column(nullable = false)
    private Instant createdAt;

    public boolean isManaged() {
        return storageDisposition == null || storageDisposition == ArtifactStorageDisposition.MANAGED;
    }

    public boolean isSourceStudy() {
        return type != null && type.isSourceStudy();
    }
}
