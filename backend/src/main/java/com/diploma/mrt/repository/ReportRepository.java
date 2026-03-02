package com.diploma.mrt.repository;

import com.diploma.mrt.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {
    Optional<Report> findByCaseEntityId(Long caseId);
}
