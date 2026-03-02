package com.diploma.mrt.service;

import com.diploma.mrt.dto.CaseDtos;
import com.diploma.mrt.entity.CaseStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CaseService {
    CaseDtos.CaseResponse create(CaseDtos.CreateCaseRequest request);
    List<CaseDtos.CaseResponse> list(CaseStatus status);
    CaseDtos.CaseResponse get(Long id);
    void upload(Long id, MultipartFile file);
    void process(Long id);
    void delete(Long id);
    List<CaseDtos.ArtifactResponse> artifacts(Long id);
    List<CaseDtos.FindingResponse> findings(Long id);
    CaseDtos.ReportResponse report(Long id);
    CaseDtos.StatusResponse status(Long id);
    CaseDtos.Viewer3DResponse viewer3d(Long id);
}
