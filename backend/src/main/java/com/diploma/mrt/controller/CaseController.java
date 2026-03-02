package com.diploma.mrt.controller;

import com.diploma.mrt.dto.CaseDtos;
import com.diploma.mrt.entity.CaseStatus;
import com.diploma.mrt.service.CaseService;
import jakarta.validation.Valid;
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
    public List<CaseDtos.CaseResponse> list(@RequestParam(required = false) CaseStatus status) {
        return caseService.list(status);
    }

    @PostMapping
    public CaseDtos.CaseResponse create(@RequestBody @Valid CaseDtos.CreateCaseRequest request) {
        return caseService.create(request);
    }

    @GetMapping("/{id}")
    public CaseDtos.CaseResponse get(@PathVariable Long id) {
        return caseService.get(id);
    }

    @PostMapping("/{id}/upload")
    public void upload(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        caseService.upload(id, file);
    }

    @PostMapping("/{id}/process")
    public void process(@PathVariable Long id) {
        caseService.process(id);
    }

    @GetMapping("/{id}/status")
    public CaseDtos.StatusResponse status(@PathVariable Long id) {
        return caseService.status(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        caseService.delete(id);
    }
}
