package com.diploma.mrt.demo.manifest;

import com.diploma.mrt.entity.ArtifactType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record DemoManifestArtifact(
        @NotNull ArtifactType type,
        @NotBlank String objectKey,
        @NotBlank String fileName,
        @NotBlank String mimeType,
        @NotBlank String sha256,
        @NotNull @PositiveOrZero Long sizeBytes
) {
}
