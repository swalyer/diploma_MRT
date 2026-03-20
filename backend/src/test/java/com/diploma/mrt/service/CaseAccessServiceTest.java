package com.diploma.mrt.service;

import com.diploma.mrt.entity.CaseEntity;
import com.diploma.mrt.entity.CaseOrigin;
import com.diploma.mrt.entity.CaseStatus;
import com.diploma.mrt.entity.Modality;
import com.diploma.mrt.entity.Role;
import com.diploma.mrt.entity.User;
import com.diploma.mrt.exception.AccessDeniedException;
import com.diploma.mrt.repository.CaseRepository;
import com.diploma.mrt.repository.UserRepository;
import com.diploma.mrt.service.impl.CaseAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CaseAccessServiceTest {
    private CaseRepository caseRepository;
    private UserRepository userRepository;
    private CaseAccessService caseAccessService;

    @BeforeEach
    void setUp() {
        caseRepository = mock(CaseRepository.class);
        userRepository = mock(UserRepository.class);
        caseAccessService = new CaseAccessService(caseRepository, userRepository);
    }

    @Test
    void seededDemoIsReadableForNonOwner() {
        CaseEntity seeded = caseEntity(41L, "admin@demo.local", Role.ADMIN, CaseOrigin.SEEDED_DEMO);
        when(caseRepository.findById(41L)).thenReturn(Optional.of(seeded));

        CaseEntity result = caseAccessService.findReadableCase("doctor@demo.local", 41L);

        assertSame(seeded, result);
    }

    @Test
    void seededDemoMutationIsRejectedForDoctor() {
        CaseEntity seeded = caseEntity(42L, "admin@demo.local", Role.ADMIN, CaseOrigin.SEEDED_DEMO);
        when(caseRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(seeded));
        when(userRepository.findByEmail("doctor@demo.local")).thenReturn(Optional.of(user("doctor@demo.local", Role.DOCTOR)));

        assertThrows(AccessDeniedException.class, () -> caseAccessService.findMutableCaseForUpdate("doctor@demo.local", 42L));
    }

    @Test
    void seededDemoMutationIsAllowedForAdmin() {
        CaseEntity seeded = caseEntity(43L, "owner@demo.local", Role.ADMIN, CaseOrigin.SEEDED_DEMO);
        when(caseRepository.findByIdForUpdate(43L)).thenReturn(Optional.of(seeded));
        when(userRepository.findByEmail("admin@demo.local")).thenReturn(Optional.of(user("admin@demo.local", Role.ADMIN)));

        assertDoesNotThrow(() -> caseAccessService.findMutableCaseForUpdate("admin@demo.local", 43L));
    }

    private CaseEntity caseEntity(Long id, String ownerEmail, Role ownerRole, CaseOrigin origin) {
        CaseEntity caseEntity = new CaseEntity();
        caseEntity.setId(id);
        caseEntity.setPatientPseudoId("P-" + id);
        caseEntity.setModality(Modality.CT);
        caseEntity.setStatus(CaseStatus.COMPLETED);
        caseEntity.setOrigin(origin);
        caseEntity.setCreatedBy(user(ownerEmail, ownerRole));
        caseEntity.setCreatedAt(Instant.now());
        caseEntity.setUpdatedAt(Instant.now());
        return caseEntity;
    }

    private User user(String email, Role role) {
        User user = new User();
        user.setId(role == Role.ADMIN ? 7L : 8L);
        user.setEmail(email);
        user.setRole(role);
        user.setCreatedAt(Instant.now());
        return user;
    }
}
