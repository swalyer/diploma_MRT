package com.diploma.mrt.repository;

import com.diploma.mrt.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findByCaseIdOrderByCreatedAtAsc(Long caseId);
}
