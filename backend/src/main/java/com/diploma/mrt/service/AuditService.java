package com.diploma.mrt.service;

import com.diploma.mrt.entity.AuditEvent;
import com.diploma.mrt.entity.AuditAction;
import com.diploma.mrt.model.ProcessDetails;

import java.util.List;

public interface AuditService {
    default void log(Long userId, Long caseId, AuditAction action) {
        log(userId, caseId, action, null);
    }

    void log(Long userId, Long caseId, AuditAction action, ProcessDetails details);
    List<AuditEvent> listByCase(Long caseId);
}
