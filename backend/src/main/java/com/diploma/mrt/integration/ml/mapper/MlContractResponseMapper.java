package com.diploma.mrt.integration.ml.mapper;

import com.diploma.mrt.entity.ArtifactStorageDisposition;
import com.diploma.mrt.entity.ArtifactType;
import com.diploma.mrt.entity.ExecutionMode;
import com.diploma.mrt.entity.FindingType;
import com.diploma.mrt.entity.InferenceStatus;
import com.diploma.mrt.entity.Modality;
import com.diploma.mrt.integration.ml.MlInferenceResult;
import com.diploma.mrt.integration.ml.contract.MlContractInferenceResponse;
import com.diploma.mrt.integration.ml.contract.MlContractTypes;
import com.diploma.mrt.model.BoundingBox;
import com.diploma.mrt.model.FindingLocation;
import com.diploma.mrt.model.MlMetrics;
import com.diploma.mrt.model.ReportCapabilities;
import com.diploma.mrt.model.ReportData;
import com.diploma.mrt.model.ReportSections;
import com.diploma.mrt.report.CanonicalReportTextAssembler;
import com.diploma.mrt.service.materialization.CaseMaterialization;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class MlContractResponseMapper {
    public MlInferenceResult toDomain(MlContractInferenceResponse response) {
        Objects.requireNonNull(response, "response must not be null");

        InferenceStatus status = mapStatus(response.status());
        MlMetrics metrics = mapMetrics(response.metrics());
        if (status != InferenceStatus.COMPLETED) {
            return new MlInferenceResult(status, response.modelVersion(), metrics, null);
        }

        return new MlInferenceResult(
                status,
                response.modelVersion(),
                metrics,
                new CaseMaterialization(
                        CaseMaterialization.ArtifactReplaceMode.GENERATED_ONLY,
                        mapArtifacts(response.artifacts()),
                        mapFindings(response.findings()),
                        mapReportSpec(Objects.requireNonNull(response.reportData(), "reportData must not be null"))
                )
        );
    }

    private CaseMaterialization.ReportSpec mapReportSpec(MlContractInferenceResponse.ReportData responseReportData) {
        ReportData reportData = mapReportData(responseReportData);
        return new CaseMaterialization.ReportSpec(
                CanonicalReportTextAssembler.assemble(Objects.requireNonNull(reportData.sections(), "reportData.sections must not be null")),
                reportData
        );
    }

    private List<CaseMaterialization.ArtifactSpec> mapArtifacts(MlContractInferenceResponse.ArtifactOutputs outputs) {
        List<CaseMaterialization.ArtifactSpec> artifacts = new ArrayList<>();
        if (outputs == null) {
            return artifacts;
        }
        addArtifact(artifacts, ArtifactType.ENHANCED_VOLUME, outputs.enhancedObjectKey(), "application/octet-stream");
        addArtifact(artifacts, ArtifactType.LIVER_MASK, outputs.liverMaskObjectKey(), "application/octet-stream");
        addArtifact(artifacts, ArtifactType.LESION_MASK, outputs.lesionMaskObjectKey(), "application/octet-stream");
        addArtifact(artifacts, ArtifactType.LIVER_MESH, outputs.liverMeshObjectKey(), "model/gltf-binary");
        addArtifact(artifacts, ArtifactType.LESION_MESH, outputs.lesionMeshObjectKey(), "model/gltf-binary");
        return artifacts;
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

    private List<CaseMaterialization.FindingSpec> mapFindings(List<MlContractInferenceResponse.Finding> findings) {
        if (findings == null) {
            return List.of();
        }
        return findings.stream()
                .map(finding -> new CaseMaterialization.FindingSpec(
                        mapFindingType(finding.type()),
                        finding.label(),
                        finding.confidence(),
                        finding.sizeMm(),
                        finding.volumeMm3(),
                        mapFindingLocation(finding.location())
                ))
                .toList();
    }

    private ReportData mapReportData(MlContractInferenceResponse.ReportData reportData) {
        return new ReportData(
                mapModality(reportData.modality()),
                mapExecutionMode(reportData.executionMode()),
                reportData.lesionCount(),
                reportData.evidenceBound(),
                mapReportSections(reportData.sections()),
                mapReportCapabilities(reportData.capabilities())
        );
    }

    private ReportSections mapReportSections(MlContractInferenceResponse.ReportSections sections) {
        if (sections == null) {
            return null;
        }
        return new ReportSections(
                sections.findings(),
                sections.impression(),
                sections.limitations(),
                sections.recommendation()
        );
    }

    private ReportCapabilities mapReportCapabilities(MlContractInferenceResponse.ReportCapabilities capabilities) {
        if (capabilities == null) {
            return null;
        }
        return new ReportCapabilities(
                capabilities.supports3dLiver(),
                capabilities.supports3dLesion()
        );
    }

    private FindingLocation mapFindingLocation(MlContractInferenceResponse.FindingLocation location) {
        if (location == null) {
            return null;
        }
        return new FindingLocation(
                location.segment(),
                location.centroid(),
                mapBoundingBox(location.bbox()),
                location.extent(),
                location.suspicion()
        );
    }

    private BoundingBox mapBoundingBox(MlContractInferenceResponse.BoundingBox boundingBox) {
        if (boundingBox == null) {
            return null;
        }
        return new BoundingBox(boundingBox.min(), boundingBox.max());
    }

    private MlMetrics mapMetrics(MlContractInferenceResponse.Metrics metrics) {
        if (metrics == null) {
            return null;
        }
        return new MlMetrics(
                mapExecutionMode(metrics.mode()),
                metrics.liverModel(),
                metrics.lesionModel(),
                metrics.medsamAvailable(),
                metrics.supportsMri3dSuspiciousZone()
        );
    }

    private InferenceStatus mapStatus(MlContractTypes.InferenceStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case STARTED -> InferenceStatus.STARTED;
            case COMPLETED -> InferenceStatus.COMPLETED;
            case FAILED -> InferenceStatus.FAILED;
        };
    }

    private FindingType mapFindingType(MlContractTypes.FindingType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case LESION -> FindingType.LESION;
        };
    }

    private Modality mapModality(MlContractTypes.Modality modality) {
        if (modality == null) {
            return null;
        }
        return switch (modality) {
            case CT -> Modality.CT;
            case MRI -> Modality.MRI;
        };
    }

    private ExecutionMode mapExecutionMode(MlContractTypes.ExecutionMode executionMode) {
        if (executionMode == null) {
            return null;
        }
        return switch (executionMode) {
            case MOCK -> ExecutionMode.MOCK;
            case REAL -> ExecutionMode.REAL;
        };
    }

    private String fileNameFromObjectKey(String objectKey) {
        int slashIndex = objectKey.lastIndexOf('/');
        return slashIndex >= 0 ? objectKey.substring(slashIndex + 1) : objectKey;
    }
}
