package com.diploma.mrt.service;

public interface AuditService {
    void log(Long userId, Long caseId, String action, String detailsJson);
}
