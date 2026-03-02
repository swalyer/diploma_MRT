package com.diploma.mrt.service;

import com.diploma.mrt.entity.AuditEvent;

import java.util.List;

public interface AuditService {
    void log(Long userId, Long caseId, String action, String detailsJson);
    List<AuditEvent> listByCase(Long caseId);
}
