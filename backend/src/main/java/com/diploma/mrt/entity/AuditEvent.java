package com.diploma.mrt.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
public class AuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private Long caseId;
    @Column(nullable = false)
    private String action;
    @Column(columnDefinition = "text")
    private String detailsJson;
    @Column(nullable = false)
    private Instant createdAt;
}
