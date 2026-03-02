package com.diploma.mrt.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    String saveCaseFile(Long caseId, MultipartFile file);
}
