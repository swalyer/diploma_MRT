package com.diploma.mrt.repository;

import com.diploma.mrt.entity.InferenceRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InferenceRunRepository extends JpaRepository<InferenceRun, Long> {
    List<InferenceRun> findByCaseEntityId(Long caseId);
}
