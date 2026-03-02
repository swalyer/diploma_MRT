package com.diploma.mrt.service.impl;

import com.diploma.mrt.service.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
public class LocalStorageService implements StorageService {
    @Value("${app.storage-root}")
    private String root;

    @Override
    public String saveCaseFile(Long caseId, MultipartFile file) {
        try {
            Path dir = Path.of(root, "cases", caseId.toString());
            Files.createDirectories(dir);
            Path target = dir.resolve(file.getOriginalFilename());
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return target.toString();
        } catch (IOException exception) {
            throw new RuntimeException("Storage error", exception);
        }
    }
}
