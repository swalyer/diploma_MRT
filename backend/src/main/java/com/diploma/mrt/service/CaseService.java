package com.diploma.mrt.service;

import com.diploma.mrt.dto.CaseDtos;
import com.diploma.mrt.entity.CaseStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CaseService {
    CaseDtos.CaseResponse create(String userEmail, CaseDtos.CreateCaseRequest request);
    List<CaseDtos.CaseResponse> list(String userEmail, CaseStatus status);
    CaseDtos.CaseResponse get(String userEmail, Long id);
    CaseDtos.ArtifactResponse upload(String userEmail, Long id, MultipartFile file);
    void process(String userEmail, Long id);
    void delete(String userEmail, Long id);
    List<CaseDtos.ArtifactResponse> artifacts(String userEmail, Long id);
    List<CaseDtos.FindingResponse> findings(String userEmail, Long id);
    CaseDtos.ReportResponse report(String userEmail, Long id);
    CaseDtos.StatusResponse status(String userEmail, Long id);
    CaseDtos.Viewer3DResponse viewer3d(String userEmail, Long id);
}
