package com.diploma.mrt.service.impl;

import com.diploma.mrt.exception.BadRequestException;
import com.diploma.mrt.service.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.zip.GZIPInputStream;

@Service
public class CaseFileService {
    private final StorageService storageService;

    public CaseFileService(StorageService storageService) {
        this.storageService = storageService;
    }

    public void validateUploadedStudy(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file is empty");
        }
        String filename = file.getOriginalFilename();
        String normalized = filename == null ? null : filename.toLowerCase();
        if (normalized == null || !(normalized.endsWith(".nii") || normalized.endsWith(".nii.gz"))) {
            throw new BadRequestException("Unsupported file format. Allowed: .nii, .nii.gz");
        }
        try (InputStream inputStream = normalized.endsWith(".nii.gz")
                ? new GZIPInputStream(file.getInputStream())
                : file.getInputStream()) {
            validateNiftiHeader(inputStream);
        } catch (IOException exception) {
            throw new BadRequestException("Invalid NIfTI file content");
        }
    }

    public void registerDeleteAfterCommit(List<String> objectKeys) {
        if (objectKeys.isEmpty() || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String objectKey : objectKeys) {
                    storageService.delete(objectKey);
                }
            }
        });
    }

    public void registerDeleteOnRollback(String objectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    storageService.delete(objectKey);
                }
            }
        });
    }

    private void validateNiftiHeader(InputStream inputStream) throws IOException {
        byte[] header = inputStream.readNBytes(352);
        if (header.length < 352) {
            throw new BadRequestException("Invalid NIfTI file: header is too short");
        }
        int headerSizeLittleEndian = ByteBuffer.wrap(header, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        int headerSizeBigEndian = ByteBuffer.wrap(header, 0, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        boolean validHeaderSize = headerSizeLittleEndian == 348 || headerSizeBigEndian == 348;
        boolean validMagic = header[344] == 'n'
                && (header[345] == '+' || header[345] == 'i')
                && header[346] == '1'
                && header[347] == 0;
        if (!validHeaderSize || !validMagic) {
            throw new BadRequestException("Invalid NIfTI file content");
        }
    }
}
