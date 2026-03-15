package com.diploma.mrt.service.materialization;

import com.diploma.mrt.demo.manifest.DemoManifest;
import com.diploma.mrt.entity.ArtifactStorageDisposition;
import com.diploma.mrt.entity.ArtifactType;
import com.diploma.mrt.model.ReportCapabilities;
import com.diploma.mrt.model.ReportData;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class DemoManifestMaterializationMapper {
    public CaseMaterialization toMaterialization(DemoManifest manifest) {
        Objects.requireNonNull(manifest, "manifest must not be null");

        return new CaseMaterialization(
                CaseMaterialization.ArtifactReplaceMode.ALL_CASE_ARTIFACTS,
                manifest.artifacts().stream()
                        .map(artifact -> new CaseMaterialization.ArtifactSpec(
                                artifact.type(),
                                artifact.objectKey(),
                                artifact.fileName(),
                                artifact.mimeType(),
                                ArtifactStorageDisposition.REFERENCED
                        ))
                        .toList(),
                manifest.findings().stream()
                        .map(finding -> new CaseMaterialization.FindingSpec(
                                finding.type(),
                                finding.label(),
                                finding.confidence(),
                                finding.sizeMm(),
                                finding.volumeMm3(),
                                finding.location()
                        ))
                        .toList(),
                new CaseMaterialization.ReportSpec(
                        manifest.reportText(),
                        new ReportData(
                                manifest.modality(),
                                null,
                                manifest.findings().size(),
                                true,
                                manifest.reportData().toSections(),
                                new ReportCapabilities(
                                        hasArtifact(manifest, ArtifactType.LIVER_MESH),
                                        hasArtifact(manifest, ArtifactType.LESION_MESH)
                                )
                        )
                )
        );
    }

    private boolean hasArtifact(DemoManifest manifest, ArtifactType type) {
        return manifest.artifacts().stream().anyMatch(artifact -> artifact.type() == type);
    }
}
