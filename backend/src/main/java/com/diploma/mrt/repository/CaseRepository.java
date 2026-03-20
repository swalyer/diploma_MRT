package com.diploma.mrt.repository;

import com.diploma.mrt.entity.CaseEntity;
import com.diploma.mrt.entity.CaseOrigin;
import com.diploma.mrt.entity.CaseStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CaseRepository extends JpaRepository<CaseEntity, Long> {
    List<CaseEntity> findByStatus(CaseStatus status);
    List<CaseEntity> findByCreatedByEmail(String email);
    List<CaseEntity> findByCreatedByEmailAndStatus(String email, CaseStatus status);
    List<CaseEntity> findByOriginOrderByUpdatedAtDesc(CaseOrigin origin);
    Optional<CaseEntity> findByDemoCaseSlugAndDemoManifestVersion(String demoCaseSlug, String demoManifestVersion);

    @Query("""
            select caseEntity from CaseEntity caseEntity
            where caseEntity.createdBy.email = :email
               or caseEntity.origin = :demoOrigin
            order by caseEntity.updatedAt desc, caseEntity.id desc
            """)
    List<CaseEntity> findReadableByEmailIncludingDemoOrigin(
            @Param("email") String email,
            @Param("demoOrigin") CaseOrigin demoOrigin
    );

    @Query("""
            select caseEntity from CaseEntity caseEntity
            where caseEntity.status = :status
              and (caseEntity.createdBy.email = :email or caseEntity.origin = :demoOrigin)
            order by caseEntity.updatedAt desc, caseEntity.id desc
            """)
    List<CaseEntity> findReadableByEmailIncludingDemoOriginAndStatus(
            @Param("email") String email,
            @Param("demoOrigin") CaseOrigin demoOrigin,
            @Param("status") CaseStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select caseEntity from CaseEntity caseEntity where caseEntity.id = :id")
    Optional<CaseEntity> findByIdForUpdate(@Param("id") Long id);
}
