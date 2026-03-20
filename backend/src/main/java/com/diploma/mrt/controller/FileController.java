package com.diploma.mrt.controller;

import com.diploma.mrt.entity.Artifact;
import com.diploma.mrt.exception.NotFoundException;
import com.diploma.mrt.repository.ArtifactRepository;
import com.diploma.mrt.service.StorageService;
import com.diploma.mrt.service.impl.CaseAccessService;
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
    private final CaseAccessService caseAccessService;

    public FileController(ArtifactRepository artifactRepository, StorageService storageService, CaseAccessService caseAccessService) {
        this.artifactRepository = artifactRepository;
        this.storageService = storageService;
        this.caseAccessService = caseAccessService;
    }

    @GetMapping("/{artifactId}/download")
    public ResponseEntity<Resource> download(Authentication authentication, @PathVariable("artifactId") Long artifactId) {
        Artifact artifact = artifactRepository.findById(artifactId).orElseThrow(() -> new NotFoundException("Artifact not found"));
        caseAccessService.ensureReadAccess(artifact.getCaseEntity(), authentication.getName());
        Resource resource = storageService.loadAsResource(artifact.getObjectKey());
        if (!resource.exists() || !resource.isReadable()) {
            throw new NotFoundException("Artifact binary not found");
        }
        String filename = artifact.getOriginalFileName();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, artifact.getMimeType())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(resource);
    }
}
