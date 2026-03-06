package com.diploma.mrt.controller;

import com.diploma.mrt.entity.Artifact;
import com.diploma.mrt.exception.AccessDeniedException;
import com.diploma.mrt.exception.NotFoundException;
import com.diploma.mrt.repository.ArtifactRepository;
import com.diploma.mrt.service.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final ArtifactRepository artifactRepository;
    private final StorageService storageService;

    public FileController(ArtifactRepository artifactRepository, StorageService storageService) {
        this.artifactRepository = artifactRepository;
        this.storageService = storageService;
    }

    @GetMapping("/{artifactId}/download")
    public ResponseEntity<Resource> download(Authentication authentication, @PathVariable("artifactId") Long artifactId) {
        Artifact artifact = artifactRepository.findById(artifactId).orElseThrow(() -> new NotFoundException("Artifact not found"));
        if (!artifact.getCaseEntity().getCreatedBy().getEmail().equals(authentication.getName())) {
            throw new AccessDeniedException("Access denied");
        }
        Resource resource = storageService.loadAsResource(artifact.getFilePath());
        if (!resource.exists() || !resource.isReadable()) {
            throw new NotFoundException("Artifact binary not found");
        }
        String filename = resource.getFilename() == null ? "artifact.bin" : resource.getFilename();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, artifact.getMimeType())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(resource);
    }
}
