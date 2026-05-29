package com.diploma.mrt.controller;

import com.diploma.mrt.dto.CaseDtos;
import com.diploma.mrt.report.ReportPdfService;
import com.diploma.mrt.service.CaseService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cases/{id}")
public class ResultController {
    private final CaseService caseService;
    private final ReportPdfService reportPdfService;

    public ResultController(CaseService caseService, ReportPdfService reportPdfService) {
        this.caseService = caseService;
        this.reportPdfService = reportPdfService;
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

    @GetMapping("/report.pdf")
    public ResponseEntity<byte[]> reportPdf(Authentication authentication, @PathVariable("id") Long id) {
        String user = authentication.getName();
        CaseDtos.CaseResponse caseResponse = caseService.get(user, id);
        CaseDtos.ReportResponse report = caseService.report(user, id);
        List<CaseDtos.FindingResponse> findings = caseService.findings(user, id);
        CaseDtos.StatusResponse status = caseService.status(user, id);
        byte[] pdf = reportPdfService.render(caseResponse, report, findings, status);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename("case-" + id + "-report.pdf").build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    @GetMapping("/viewer/3d")
    public CaseDtos.Viewer3DResponse viewer(Authentication authentication, @PathVariable("id") Long id) {
        return caseService.viewer3d(authentication.getName(), id);
    }
}
