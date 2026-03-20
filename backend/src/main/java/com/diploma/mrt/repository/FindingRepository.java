package com.diploma.mrt.repository;

import com.diploma.mrt.entity.Finding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FindingRepository extends JpaRepository<Finding, Long> {
    List<Finding> findByCaseEntityId(Long caseId);
    void deleteByCaseEntityId(Long caseId);
}
