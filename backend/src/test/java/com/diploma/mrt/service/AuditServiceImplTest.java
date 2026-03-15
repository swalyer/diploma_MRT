package com.diploma.mrt.service;

import com.diploma.mrt.entity.AuditAction;
import com.diploma.mrt.model.ProcessDetails;
import com.diploma.mrt.service.impl.AuditServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditServiceImplTest {
    @Test
    void logDoesNotFailCriticalFlowWhenAuditStorageIsDown() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbcTemplate);
        doThrow(new DataAccessResourceFailureException("audit down"))
                .when(jdbcTemplate)
                .update(anyString(), eq(1L), eq(2L), eq("CASE_CREATED"), anyString(), any());

        AuditServiceImpl auditService = new AuditServiceImpl(provider, true);

        assertDoesNotThrow(() -> auditService.log(1L, 2L, AuditAction.CASE_CREATED, ProcessDetails.empty()));
    }

    @Test
    void listByCaseFallsBackToEmptyWhenAuditStorageIsDown() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbcTemplate);
        doThrow(new DataAccessResourceFailureException("audit down"))
                .when(jdbcTemplate)
                .query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(7L));

        AuditServiceImpl auditService = new AuditServiceImpl(provider, true);

        assertEquals(List.of(), auditService.listByCase(7L));
    }
}
