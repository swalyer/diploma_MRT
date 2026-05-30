package com.diploma.mrt.controller;

import com.diploma.mrt.dto.CaseDtos;
import com.diploma.mrt.dto.ComparisonDtos;
import com.diploma.mrt.service.ComparisonService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cases/{id}/comparison")
public class ComparisonController {
    private final ComparisonService comparisonService;

    public ComparisonController(ComparisonService comparisonService) {
        this.comparisonService = comparisonService;
    }

    @GetMapping("/candidates")
    public List<CaseDtos.CaseResponse> candidates(Authentication authentication, @PathVariable("id") Long id) {
        return comparisonService.candidates(authentication.getName(), id);
    }

    @GetMapping
    public ComparisonDtos.ComparisonResponse compare(Authentication authentication,
                                                     @PathVariable("id") Long id,
                                                     @RequestParam("against") Long against) {
        // Convention: the case in the path is the follow-up, compared against an earlier baseline.
        return comparisonService.compare(authentication.getName(), against, id);
    }
}
