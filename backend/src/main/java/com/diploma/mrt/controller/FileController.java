package com.diploma.mrt.controller;

import com.diploma.mrt.entity.Artifact;
import com.diploma.mrt.exception.NotFoundException;
import com.diploma.mrt.repository.ArtifactRepository;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final ArtifactRepository artifactRepository;

    public FileController(ArtifactRepository artifactRepository) {
        this.artifactRepository = artifactRepository;
    }

    @GetMapping("/{artifactId}/download")
    public ResponseEntity<FileSystemResource> download(@PathVariable Long artifactId) {
        Artifact artifact = artifactRepository.findById(artifactId).orElseThrow(() -> new NotFoundException("Artifact not found"));
        FileSystemResource resource = new FileSystemResource(artifact.getFilePath());
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, artifact.getMimeType()).body(resource);
    }
}
