package com.diploma.mrt.service;

import com.diploma.mrt.service.impl.LocalStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalStorageServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void rejectsObjectKeysOutsideStorageRoot() {
        LocalStorageService storageService = new LocalStorageService();
        ReflectionTestUtils.setField(storageService, "root", tempDir.toString());

        assertThrows(IllegalArgumentException.class, () -> storageService.validateObjectKey("../secrets.txt"));
        assertThrows(IllegalArgumentException.class, () -> storageService.loadAsResource("../../../tmp/escape.bin"));
    }

    @Test
    void acceptsObjectKeysInsideStorageRoot() {
        LocalStorageService storageService = new LocalStorageService();
        ReflectionTestUtils.setField(storageService, "root", tempDir.toString());

        assertDoesNotThrow(() -> storageService.validateObjectKey("cases/1/output.nii.gz"));
        assertDoesNotThrow(() -> storageService.loadAsResource("cases/1/output.nii.gz"));
    }

    @Test
    void saveCaseFileGeneratesUniqueObjectKeysForSameFilename() throws Exception {
        LocalStorageService storageService = new LocalStorageService();
        ReflectionTestUtils.setField(storageService, "root", tempDir.toString());

        MockMultipartFile file = new MockMultipartFile("file", "study.nii.gz", "application/octet-stream", "test".getBytes());

        String firstObjectKey = storageService.saveCaseFile(1L, file);
        String secondObjectKey = storageService.saveCaseFile(1L, file);

        assertNotEquals(firstObjectKey, secondObjectKey);
        assertTrue(Files.exists(tempDir.resolve(firstObjectKey)));
        assertTrue(Files.exists(tempDir.resolve(secondObjectKey)));
    }
}
