package com.diploma.mrt.repository;

import com.diploma.mrt.entity.CaseEntity;
import com.diploma.mrt.entity.CaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CaseRepository extends JpaRepository<CaseEntity, Long> {
    List<CaseEntity> findByStatus(CaseStatus status);
}
