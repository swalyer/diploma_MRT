package com.diploma.mrt.controller;

import com.diploma.mrt.dto.CaseDtos;
import com.diploma.mrt.service.CaseService;
import org.springframework.security.core.Authentication;
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
    public List<CaseDtos.ArtifactResponse> artifacts(Authentication authentication, @PathVariable("id") Long id) {
        return caseService.artifacts(authentication.getName(), id);
    }

    @GetMapping("/findings")
    public List<CaseDtos.FindingResponse> findings(Authentication authentication, @PathVariable("id") Long id) {
        return caseService.findings(authentication.getName(), id);
    }

    @GetMapping("/report")
    public CaseDtos.ReportResponse report(Authentication authentication, @PathVariable("id") Long id) {
        return caseService.report(authentication.getName(), id);
    }

    @GetMapping("/viewer/3d")
    public CaseDtos.Viewer3DResponse viewer(Authentication authentication, @PathVariable("id") Long id) {
        return caseService.viewer3d(authentication.getName(), id);
    }
}
