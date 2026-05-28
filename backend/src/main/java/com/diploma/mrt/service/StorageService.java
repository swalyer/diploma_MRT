package com.diploma.mrt.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    String saveCaseFile(Long caseId, MultipartFile file);
    Resource loadAsResource(String objectKey);
    void validateObjectKey(String objectKey);
    void delete(String objectKey);
}
