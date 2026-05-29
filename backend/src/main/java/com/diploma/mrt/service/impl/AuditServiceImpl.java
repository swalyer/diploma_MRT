package com.diploma.mrt.service.impl;

import com.diploma.mrt.entity.AuditAction;
import com.diploma.mrt.entity.AuditEvent;
import com.diploma.mrt.events.CaseEventPublisher;
import com.diploma.mrt.model.ProcessDetails;
import com.diploma.mrt.service.AuditService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuditServiceImpl implements AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);

    private final JdbcTemplate auditJdbcTemplate;
    private final boolean auditDbEnabled;
    private final CaseEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public AuditServiceImpl(
            @Qualifier("auditJdbcTemplate") ObjectProvider<JdbcTemplate> auditJdbcTemplateProvider,
            CaseEventPublisher eventPublisher,
            @Value("${app.audit.enabled:false}") boolean auditDbEnabled
    ) {
        this.auditJdbcTemplate = auditJdbcTemplateProvider.getIfAvailable();
        this.eventPublisher = eventPublisher;
        this.auditDbEnabled = auditDbEnabled && this.auditJdbcTemplate != null;
    }

    @Override
    public void log(Long userId, Long caseId, AuditAction action, ProcessDetails details) {
        Instant createdAt = Instant.now();
        publishStageEvent(caseId, action, details, createdAt);
        if (auditDbEnabled) {
            try {
                auditJdbcTemplate.update(
                        "INSERT INTO audit_event (user_id, case_id, action, details_json, created_at) VALUES (?, ?, ?, ?::jsonb, ?)",
                        userId,
                        caseId,
                        action.name(),
                        serialize(details),
                        Timestamp.from(createdAt)
                );
            } catch (RuntimeException exception) {
                log.warn("Audit log write skipped for action={} caseId={}: {}", action, caseId, exception.getMessage());
            }
            return;
        }
    }

    private void publishStageEvent(Long caseId, AuditAction action, ProcessDetails details, Instant createdAt) {
        if (caseId == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("action", action.name());
            payload.put("at", createdAt.toString());
            if (details != null) {
                payload.put("stage", details.stage());
                payload.put("message", details.message());
            }
            eventPublisher.publish(caseId, "stage", payload);
        } catch (RuntimeException exception) {
            log.debug("Stage event publish skipped for caseId={}: {}", caseId, exception.toString());
        }
    }

    @Override
    public List<AuditEvent> listByCase(Long caseId) {
        if (auditDbEnabled) {
            try {
                return auditJdbcTemplate.query(
                        "SELECT id, user_id, case_id, action, details_json::text, created_at FROM audit_event WHERE case_id = ? ORDER BY created_at ASC",
                        (rs, rowNum) -> {
                            AuditEvent event = new AuditEvent();
                            event.setId(rs.getLong("id"));
                            event.setUserId(rs.getLong("user_id"));
                            event.setCaseId(rs.getLong("case_id"));
                            event.setAction(AuditAction.valueOf(rs.getString("action")));
                            event.setDetails(deserialize(rs.getString("details_json")));
                            event.setCreatedAt(rs.getTimestamp("created_at").toInstant());
                            return event;
                        },
                        caseId
                );
            } catch (RuntimeException exception) {
                log.warn("Audit log read skipped for caseId={}: {}", caseId, exception.getMessage());
            }
        }
        return List.of();
    }

    private String serialize(ProcessDetails details) {
        try {
            return objectMapper.writeValueAsString(details == null ? ProcessDetails.empty() : details);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize audit details", exception);
        }
    }

    private ProcessDetails deserialize(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(payload, ProcessDetails.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to deserialize audit details", exception);
        }
    }
}
