package com.diploma.mrt.controller;

import com.diploma.mrt.dto.CaseDtos;
import com.diploma.mrt.service.CaseService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cases/{id}")
public class ResultController {
    private final CaseService caseService;

    public ResultController(CaseService caseService) {
        this.caseService = caseService;
    }

    @GetMapping("/artifacts")
    public List<CaseDtos.ArtifactResponse> artifacts(@PathVariable Long id) {
        return caseService.artifacts(id);
    }

    @GetMapping("/findings")
    public List<CaseDtos.FindingResponse> findings(@PathVariable Long id) {
        return caseService.findings(id);
    }

    @GetMapping("/report")
    public CaseDtos.ReportResponse report(@PathVariable Long id) {
        return caseService.report(id);
    }

    @GetMapping("/viewer/3d")
    public CaseDtos.Viewer3DResponse viewer(@PathVariable Long id) {
        return caseService.viewer3d(id);
    }
}
