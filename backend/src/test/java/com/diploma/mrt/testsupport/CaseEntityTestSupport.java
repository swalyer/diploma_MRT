package com.diploma.mrt.testsupport;

import com.diploma.mrt.entity.CaseEntity;
import com.diploma.mrt.entity.CaseOrigin;
import com.diploma.mrt.entity.CaseStatus;
import com.diploma.mrt.entity.Modality;
import com.diploma.mrt.entity.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

public final class CaseEntityTestSupport {
    private CaseEntityTestSupport() {
    }

    public static CaseEntity assignId(CaseEntity entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    public static CaseEntity withStatus(CaseEntity entity, CaseStatus status) {
        ReflectionTestUtils.setField(entity, "status", status);
        return entity;
    }

    public static CaseEntity withOrigin(CaseEntity entity, CaseOrigin origin) {
        ReflectionTestUtils.setField(entity, "origin", origin);
        return entity;
    }

    public static CaseEntity withCreatedAt(CaseEntity entity, Instant createdAt) {
        ReflectionTestUtils.setField(entity, "createdAt", createdAt);
        return entity;
    }

    public static CaseEntity newPersistedLive(Long id, User createdBy, Modality modality, CaseStatus status) {
        CaseEntity entity = CaseEntity.newLive(createdBy, "P-" + id, modality, Instant.now());
        return withStatus(assignId(entity, id), status);
    }

    public static CaseEntity newPersistedSeeded(Long id, User createdBy, Modality modality, CaseStatus status) {
        return withOrigin(newPersistedLive(id, createdBy, modality, status), CaseOrigin.SEEDED_DEMO);
    }
}
