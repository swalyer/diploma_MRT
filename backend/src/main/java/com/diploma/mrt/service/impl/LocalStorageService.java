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
import java.util.UUID;

@Service
public class LocalStorageService implements StorageService {
    @Value("${app.storage-root}")
    private String root;

    @Override
    public String saveCaseFile(Long caseId, MultipartFile file) {
        String safeName = Path.of(file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename()).getFileName().toString();
        String objectKey = Path.of("cases", caseId.toString(), UUID.randomUUID().toString(), safeName)
                .toString()
                .replace('\\', '/');
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
        return new PathResource(resolvePath(objectKey));
    }

    @Override
    public void validateObjectKey(String objectKey) {
        resolvePath(objectKey);
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolvePath(objectKey));
        } catch (IOException exception) {
            throw new RuntimeException("Storage delete error", exception);
        }
    }

    private Path resolvePath(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("Object key must not be blank");
        }
        Path rootPath = Path.of(root).toAbsolutePath().normalize();
        Path resolved = rootPath.resolve(objectKey).normalize();
        if (!resolved.startsWith(rootPath)) {
            throw new IllegalArgumentException("Object key resolves outside storage root");
        }
        return resolved;
    }
}
