package com.diploma.mrt.repository;

import com.diploma.mrt.entity.Artifact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArtifactRepository extends JpaRepository<Artifact, Long> {
    List<Artifact> findByCaseEntityId(Long caseId);
}
