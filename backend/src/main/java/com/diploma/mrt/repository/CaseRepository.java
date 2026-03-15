package com.diploma.mrt.repository;

import com.diploma.mrt.entity.CaseEntity;
import com.diploma.mrt.entity.CaseStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface CaseRepository extends JpaRepository<CaseEntity, Long> {
    List<CaseEntity> findByStatus(CaseStatus status);
    List<CaseEntity> findByCreatedByEmail(String email);
    List<CaseEntity> findByCreatedByEmailAndStatus(String email, CaseStatus status);
    Optional<CaseEntity> findByDemoCaseSlugAndDemoManifestVersion(String demoCaseSlug, String demoManifestVersion);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CaseEntity> findByIdForUpdate(Long id);
}
