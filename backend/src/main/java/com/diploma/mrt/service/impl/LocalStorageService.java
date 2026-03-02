package com.diploma.mrt.service.impl;

import com.diploma.mrt.service.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
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
        String safeName = Path.of(file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename()).getFileName().toString();
        String objectKey = "cases/" + caseId + "/" + safeName;
        try {
            Path target = Path.of(root).resolve(objectKey);
            Files.createDirectories(target.getParent());
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return objectKey;
        } catch (IOException exception) {
            throw new RuntimeException("Storage error", exception);
        }
    }

    @Override
    public Resource loadAsResource(String objectKey) {
        return new PathResource(Path.of(root).resolve(objectKey));
    }

    @Override
    public String resolveAbsolutePath(String objectKey) {
        return Path.of(root).resolve(objectKey).toString();
    }
}
