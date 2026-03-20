package com.diploma.mrt.service.impl;

import com.diploma.mrt.entity.CaseEntity;
import com.diploma.mrt.entity.CaseOrigin;
import com.diploma.mrt.entity.Role;
import com.diploma.mrt.entity.User;
import com.diploma.mrt.exception.AccessDeniedException;
import com.diploma.mrt.exception.NotFoundException;
import com.diploma.mrt.repository.CaseRepository;
import com.diploma.mrt.repository.UserRepository;
import com.diploma.mrt.util.EmailNormalizer;
import org.springframework.stereotype.Service;

@Service
public class CaseAccessService {
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;

    public CaseAccessService(CaseRepository caseRepository, UserRepository userRepository) {
        this.caseRepository = caseRepository;
        this.userRepository = userRepository;
    }

    public User findUser(String email) {
        return userRepository.findByEmail(EmailNormalizer.normalize(email))
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    public CaseEntity findReadableCase(String userEmail, Long id) {
        String normalizedEmail = EmailNormalizer.normalize(userEmail);
        CaseEntity caseEntity = caseRepository.findById(id).orElseThrow(() -> new NotFoundException("Case not found"));
        ensureReadAccess(caseEntity, normalizedEmail);
        return caseEntity;
    }

    public CaseEntity findOwnedCase(String userEmail, Long id) {
        return findReadableCase(userEmail, id);
    }

    public CaseEntity findMutableCaseForUpdate(String userEmail, Long id) {
        User user = findUser(userEmail);
        CaseEntity caseEntity = caseRepository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Case not found"));
        ensureMutationAccess(caseEntity, user);
        return caseEntity;
    }

    public CaseEntity findOwnedCaseForUpdate(String userEmail, Long id) {
        return findMutableCaseForUpdate(userEmail, id);
    }

    public void ensureReadAccess(CaseEntity caseEntity, String userEmail) {
        String normalizedEmail = EmailNormalizer.normalize(userEmail);
        if (caseEntity.effectiveOrigin() == CaseOrigin.SEEDED_DEMO) {
            return;
        }
        ensureOwnership(caseEntity, normalizedEmail);
    }

    public void ensureMutationAccess(CaseEntity caseEntity, User user) {
        if (caseEntity.effectiveOrigin() == CaseOrigin.SEEDED_DEMO) {
            if (user == null || user.getRole() != Role.ADMIN) {
                throw new AccessDeniedException("Access denied");
            }
            return;
        }
        ensureOwnership(caseEntity, EmailNormalizer.normalize(user.getEmail()));
    }

    private void ensureOwnership(CaseEntity caseEntity, String normalizedEmail) {
        if (!caseEntity.getCreatedBy().getEmail().equals(normalizedEmail)) {
            throw new AccessDeniedException("Access denied");
        }
    }
}
