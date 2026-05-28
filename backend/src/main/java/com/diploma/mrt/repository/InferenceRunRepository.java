package com.diploma.mrt.repository;

import com.diploma.mrt.entity.InferenceRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface InferenceRunRepository extends JpaRepository<InferenceRun, Long> {
    List<InferenceRun> findByCaseEntityId(Long caseId);
    List<InferenceRun> findByCaseEntityIdOrderByStartedAtDesc(Long caseId);
    @Query("""
            select run from InferenceRun run
            where run.caseEntity.id in :caseIds
              and run.startedAt = (
                select max(innerRun.startedAt)
                from InferenceRun innerRun
                where innerRun.caseEntity.id = run.caseEntity.id
              )
            """)
    List<InferenceRun> findLatestByCaseIds(@Param("caseIds") Collection<Long> caseIds);
    void deleteByCaseEntityId(Long caseId);
}
