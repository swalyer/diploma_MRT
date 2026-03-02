package com.diploma.mrt.service.impl;

import com.diploma.mrt.entity.AuditEvent;
import com.diploma.mrt.repository.AuditEventRepository;
import com.diploma.mrt.service.AuditService;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AuditServiceImpl implements AuditService {
    private final AuditEventRepository auditEventRepository;

    public AuditServiceImpl(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Override
    public void log(Long userId, Long caseId, String action, String detailsJson) {
        AuditEvent event = new AuditEvent();
        event.setUserId(userId);
        event.setCaseId(caseId);
        event.setAction(action);
        event.setDetailsJson(detailsJson);
        event.setCreatedAt(Instant.now());
        auditEventRepository.save(event);
    }
}
