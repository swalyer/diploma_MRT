package com.diploma.mrt.service.impl;

import com.diploma.mrt.entity.AuditEvent;
import com.diploma.mrt.repository.AuditEventRepository;
import com.diploma.mrt.service.AuditService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
public class AuditServiceImpl implements AuditService {
    private final AuditEventRepository auditEventRepository;
    private final JdbcTemplate auditJdbcTemplate;
    private final boolean auditDbEnabled;

    public AuditServiceImpl(
            AuditEventRepository auditEventRepository,
            @Qualifier("auditJdbcTemplate") ObjectProvider<JdbcTemplate> auditJdbcTemplateProvider,
            @Value("${app.audit.enabled:false}") boolean auditDbEnabled
    ) {
        this.auditEventRepository = auditEventRepository;
        this.auditJdbcTemplate = auditJdbcTemplateProvider.getIfAvailable();
        this.auditDbEnabled = auditDbEnabled && this.auditJdbcTemplate != null;
    }

    @Override
    public void log(Long userId, Long caseId, String action, String detailsJson) {
        Instant createdAt = Instant.now();
        if (auditDbEnabled) {
            auditJdbcTemplate.update(
                    "INSERT INTO audit_event (user_id, case_id, action, details_json, created_at) VALUES (?, ?, ?, ?::jsonb, ?)",
                    userId,
                    caseId,
                    action,
                    detailsJson,
                    Timestamp.from(createdAt)
            );
            return;
        }

        AuditEvent event = new AuditEvent();
        event.setUserId(userId);
        event.setCaseId(caseId);
        event.setAction(action);
        event.setDetailsJson(detailsJson);
        event.setCreatedAt(createdAt);
        auditEventRepository.save(event);
    }

    @Override
    public List<AuditEvent> listByCase(Long caseId) {
        if (auditDbEnabled) {
            return auditJdbcTemplate.query(
                    "SELECT id, user_id, case_id, action, details_json::text, created_at FROM audit_event WHERE case_id = ? ORDER BY created_at ASC",
                    (rs, rowNum) -> {
                        AuditEvent event = new AuditEvent();
                        event.setId(rs.getLong("id"));
                        event.setUserId(rs.getLong("user_id"));
                        event.setCaseId(rs.getLong("case_id"));
                        event.setAction(rs.getString("action"));
                        event.setDetailsJson(rs.getString("details_json"));
                        event.setCreatedAt(rs.getTimestamp("created_at").toInstant());
                        return event;
                    },
                    caseId
            );
        }
        return auditEventRepository.findByCaseIdOrderByCreatedAtAsc(caseId);
    }
}
