package com.diploma.mrt.controller;

import com.diploma.mrt.dto.CaseDtos;
import com.diploma.mrt.entity.CaseStatus;
import com.diploma.mrt.service.CaseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/cases")
public class CaseController {
    private final CaseService caseService;

    public CaseController(CaseService caseService) {
        this.caseService = caseService;
    }

    @GetMapping
    public List<CaseDtos.CaseResponse> list(Authentication authentication,
                                            @RequestParam(name = "status", required = false) CaseStatus status) {
        return caseService.list(authentication.getName(), status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CaseDtos.CaseResponse create(Authentication authentication, @RequestBody @Valid CaseDtos.CreateCaseRequest request) {
        return caseService.create(authentication.getName(), request);
    }

    @GetMapping("/{id}")
    public CaseDtos.CaseResponse get(Authentication authentication, @PathVariable("id") Long id) {
        return caseService.get(authentication.getName(), id);
    }

    @PostMapping("/{id}/upload")
    @ResponseStatus(HttpStatus.CREATED)
    public CaseDtos.ArtifactResponse upload(Authentication authentication,
                                            @PathVariable("id") Long id,
                                            @RequestParam(name = "file") MultipartFile file) {
        return caseService.upload(authentication.getName(), id, file);
    }

    @PostMapping("/{id}/process")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void process(Authentication authentication, @PathVariable("id") Long id) {
        caseService.process(authentication.getName(), id);
    }

    @GetMapping("/{id}/status")
    public CaseDtos.StatusResponse status(Authentication authentication, @PathVariable("id") Long id) {
        return caseService.status(authentication.getName(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication, @PathVariable("id") Long id) {
        caseService.delete(authentication.getName(), id);
    }
}
