package com.diploma.mrt.entity;

import com.diploma.mrt.model.ProcessDetails;
import com.diploma.mrt.persistence.converter.ProcessDetailsJsonConverter;

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
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;
    @Convert(converter = ProcessDetailsJsonConverter.class)
    @Column(name = "details_json", columnDefinition = "text")
    private ProcessDetails details;
    @Column(nullable = false)
    private Instant createdAt;
}
