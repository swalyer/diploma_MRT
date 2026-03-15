package com.diploma.mrt.service.materialization;

import com.diploma.mrt.client.contract.MlInferenceResponse;
import com.diploma.mrt.entity.ArtifactStorageDisposition;
import com.diploma.mrt.entity.ArtifactType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class MlInferenceMaterializationMapper {
    public CaseMaterialization toMaterialization(MlInferenceResponse response) {
        Objects.requireNonNull(response, "response must not be null");

        List<CaseMaterialization.ArtifactSpec> artifacts = new ArrayList<>();
        MlInferenceResponse.ArtifactOutputs outputs = response.artifacts();
        if (outputs != null) {
            addArtifact(artifacts, ArtifactType.ENHANCED_VOLUME, outputs.enhancedObjectKey(), "application/octet-stream");
            addArtifact(artifacts, ArtifactType.LIVER_MASK, outputs.liverMaskObjectKey(), "application/octet-stream");
            addArtifact(artifacts, ArtifactType.LESION_MASK, outputs.lesionMaskObjectKey(), "application/octet-stream");
            addArtifact(artifacts, ArtifactType.LIVER_MESH, outputs.liverMeshObjectKey(), "model/gltf-binary");
            addArtifact(artifacts, ArtifactType.LESION_MESH, outputs.lesionMeshObjectKey(), "model/gltf-binary");
        }

        List<CaseMaterialization.FindingSpec> findings = response.findings() == null
                ? List.of()
                : response.findings().stream()
                .map(finding -> new CaseMaterialization.FindingSpec(
                        finding.type(),
                        finding.label(),
                        finding.confidence(),
                        finding.sizeMm(),
                        finding.volumeMm3(),
                        finding.location()
                ))
                .toList();

        return new CaseMaterialization(
                CaseMaterialization.ArtifactReplaceMode.GENERATED_ONLY,
                artifacts,
                findings,
                new CaseMaterialization.ReportSpec(
                        response.reportText(),
                        Objects.requireNonNull(response.reportData(), "reportData must not be null")
                )
        );
    }

    private void addArtifact(
            List<CaseMaterialization.ArtifactSpec> artifacts,
            ArtifactType type,
            String objectKey,
            String mimeType
    ) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        artifacts.add(new CaseMaterialization.ArtifactSpec(
                type,
                objectKey,
                fileNameFromObjectKey(objectKey),
                mimeType,
                ArtifactStorageDisposition.MANAGED
        ));
    }

    private String fileNameFromObjectKey(String objectKey) {
        int slashIndex = objectKey.lastIndexOf('/');
        return slashIndex >= 0 ? objectKey.substring(slashIndex + 1) : objectKey;
    }
}
