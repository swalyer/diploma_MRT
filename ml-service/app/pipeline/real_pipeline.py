from pathlib import Path
import nibabel as nib
import numpy as np
from app.adapters.totalsegmentator_adapter import TotalSegmentatorAdapter
from app.adapters.nnunet_adapter import NnUnetAdapter
from app.adapters.medsam_adapter import MedSamAdapter
from app.pipeline.base import Pipeline
from app.postprocessing.mask_ops import lesion_components
from app.postprocessing.mesh_generation import mask_to_glb
from app.postprocessing.report_builder import ReportBuildInput, build_report
from app.preprocessing.dicom_to_nifti import to_nifti
from app.preprocessing.validation import validate_request
from app.schemas.common import ExecutionMode, FindingType, Modality
from app.schemas.infer_request import InferRequest
from app.schemas.infer_response import ArtifactOutputs, BoundingBox, Finding, FindingLocation, InferResponse, Metrics


class RealPipeline(Pipeline):
    def __init__(self, artifacts_root: str, totalsegmentator: TotalSegmentatorAdapter, nnunet: NnUnetAdapter, medsam: MedSamAdapter):
        self.artifacts_root = artifacts_root
        self.totalsegmentator = totalsegmentator
        self.nnunet = nnunet
        self.medsam = medsam

    def run(self, request: InferRequest) -> InferResponse:
        validate_request(request)
        input_key = request.fileReferences.inputObjectKey
        nii_key = to_nifti(input_key, self.artifacts_root)
        liver_mask_key, liver_real = self.totalsegmentator.segment_liver(nii_key, f'{nii_key}.liver_mask.nii.gz', self.artifacts_root, request.modality)

        experimental = request.modality == Modality.MRI
        lesion_key = f'{nii_key}.lesion_mask.nii.gz'
        if experimental:
            lesion_key, lesion_real = self.medsam.segment_lesion(nii_key, liver_mask_key, lesion_key, self.artifacts_root, request.modality)
        else:
            lesion_key, lesion_real = self.nnunet.segment_lesion(nii_key, liver_mask_key, lesion_key, self.artifacts_root, request.modality)

        lesion_image = nib.load(str(Path(self.artifacts_root) / lesion_key))
        lesion_data = lesion_image.get_fdata()
        spacing = tuple(float(value) for value in lesion_image.header.get_zooms()[:3])
        lesion_count, components = lesion_components(lesion_data)
        findings = [
            Finding(
                type=FindingType.LESION,
                label=f'Lesion component #{c["id"]}',
                confidence=None,
                sizeMm=float(round(max(c['extent'][i] * spacing[i] for i in range(3)), 2)),
                volumeMm3=float(round(float(c['voxels']) * np.prod(spacing), 2)),
                location=FindingLocation(
                    centroid=[float(value) for value in c['centroid']],
                    bbox=BoundingBox(
                        min=[int(value) for value in c['bbox']['min']],
                        max=[int(value) for value in c['bbox']['max']],
                    ),
                    extent=[int(value) for value in c['extent']],
                ),
            )
            for c in components
        ]

        liver_mesh = mask_to_glb(liver_mask_key, f'{nii_key}.liver.glb', self.artifacts_root)
        lesion_mesh = mask_to_glb(lesion_key, f'{nii_key}.lesion.glb', self.artifacts_root) if lesion_count > 0 else None
        report_text, report_data = build_report(
            ReportBuildInput(
                modality=request.modality,
                execution_mode=ExecutionMode.REAL,
                lesion_count=lesion_count,
                liver_model=liver_real,
                lesion_model=lesion_real,
                supports_3d_liver=True,
                supports_3d_lesion=lesion_mesh is not None,
            )
        )
        return InferResponse(
            status='COMPLETED',
            modelVersion=f"real-ts:{'on' if liver_real else 'heuristic'}-lesion:{'model' if lesion_real else 'heuristic'}",
            metrics=Metrics(
                mode=ExecutionMode.REAL,
                liverModel=liver_real,
                lesionModel=lesion_real,
                medsamAvailable=self.medsam.interactive_available(),
                supportsMri3dSuspiciousZone=True,
            ),
            reportText=report_text,
            reportData=report_data,
            findings=findings,
            artifacts=ArtifactOutputs(
                enhancedObjectKey=nii_key,
                liverMaskObjectKey=liver_mask_key,
                lesionMaskObjectKey=lesion_key,
                liverMeshObjectKey=liver_mesh,
                lesionMeshObjectKey=lesion_mesh,
            ),
        )
