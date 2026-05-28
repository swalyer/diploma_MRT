import shutil
import zlib
from pathlib import Path

import nibabel as nib
import numpy as np
import trimesh

from app.pipeline.base import Pipeline
from app.postprocessing.report_builder import ReportBuildInput, build_report
from app.schemas.common import ExecutionMode, FindingType
from app.schemas.infer_request import InferRequest
from app.schemas.infer_response import ArtifactOutputs, Finding, FindingLocation, InferResponse, Metrics


class MockPipeline(Pipeline):
    def __init__(self, artifacts_root: str):
        self.artifacts_root = artifacts_root

    def run(self, request: InferRequest) -> InferResponse:
        input_key = request.fileReferences.inputObjectKey
        input_path = Path(self.artifacts_root) / input_key
        image = nib.load(str(input_path))
        volume_shape = image.shape[:3]
        suspicious = self._simulated_lesion_enabled(input_path)
        enhanced_key = f'{input_key}.enhanced.nii.gz'
        liver_mask_key = f'{input_key}.liver_mask.nii.gz'
        lesion_mask_key = f'{input_key}.lesion_mask.nii.gz'
        liver_mesh_key = f'{input_key}.liver.glb'
        lesion_mesh_key = f'{input_key}.lesion.glb' if suspicious else None
        lesion_mask = self._lesion_mask(volume_shape, suspicious)

        self._copy_volume(input_path, enhanced_key)
        self._save_mask(image, self._full_volume_mask(volume_shape), liver_mask_key)
        self._save_mask(image, lesion_mask, lesion_mask_key)
        self._save_mesh(liver_mesh_key, 0.45)
        if lesion_mesh_key is not None:
            self._save_mesh(lesion_mesh_key, 0.18)

        findings = self._build_findings(image, lesion_mask) if suspicious else []
        report_text, report_data = build_report(
            ReportBuildInput(
                modality=request.modality,
                execution_mode=ExecutionMode.MOCK,
                lesion_count=len(findings),
                liver_model=None,
                lesion_model=None,
                supports_3d_liver=True,
                supports_3d_lesion=lesion_mesh_key is not None,
            )
        )
        return InferResponse(
            status='COMPLETED',
            modelVersion='mock-v2',
            metrics=Metrics(mode=ExecutionMode.MOCK),
            reportText=report_text,
            reportData=report_data,
            findings=findings,
            artifacts=ArtifactOutputs(
                enhancedObjectKey=enhanced_key,
                liverMaskObjectKey=liver_mask_key,
                lesionMaskObjectKey=lesion_mask_key,
                liverMeshObjectKey=liver_mesh_key,
                lesionMeshObjectKey=lesion_mesh_key,
            ),
        )

    def _copy_volume(self, input_path: Path, output_key: str) -> None:
        out_path = Path(self.artifacts_root) / output_key
        out_path.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(input_path, out_path)

    def _save_mask(self, image: nib.spatialimages.SpatialImage, mask: np.ndarray, output_key: str) -> None:
        out_path = Path(self.artifacts_root) / output_key
        out_path.parent.mkdir(parents=True, exist_ok=True)
        nib.save(nib.Nifti1Image(mask.astype(np.uint8), image.affine), str(out_path))

    def _save_mesh(self, output_key: str, radius: float) -> None:
        out_path = Path(self.artifacts_root) / output_key
        out_path.parent.mkdir(parents=True, exist_ok=True)
        trimesh.creation.icosphere(subdivisions=1, radius=radius).export(str(out_path))

    def _full_volume_mask(self, shape: tuple[int, int, int]) -> np.ndarray:
        return np.ones(shape, dtype=np.uint8)

    def _lesion_mask(self, shape: tuple[int, int, int], suspicious: bool) -> np.ndarray:
        mask = np.zeros(shape, dtype=np.uint8)
        if not suspicious:
            return mask
        starts = [max(0, dim // 3) for dim in shape]
        ends = [min(dim, start + max(1, dim // 4)) for dim, start in zip(shape, starts)]
        mask[starts[0]:ends[0], starts[1]:ends[1], starts[2]:ends[2]] = 1
        return mask

    def _build_findings(self, image: nib.spatialimages.SpatialImage, lesion_mask: np.ndarray) -> list[Finding]:
        spacing = tuple(float(value) for value in image.header.get_zooms()[:3])
        coords = np.argwhere(lesion_mask > 0)
        if coords.size == 0:
            return []
        bbox_min = coords.min(axis=0).astype(int)
        bbox_max = coords.max(axis=0).astype(int)
        extent = (bbox_max - bbox_min + 1).astype(int)
        size_mm = float(round(max(extent[i] * spacing[i] for i in range(3)), 2))
        volume_mm3 = float(round(float(coords.shape[0]) * np.prod(spacing), 2))
        centroid = coords.mean(axis=0).tolist()
        return [
            Finding(
                type=FindingType.LESION,
                label='Simulated lesion component',
                confidence=None,
                sizeMm=size_mm,
                volumeMm3=volume_mm3,
                location=FindingLocation(
                    centroid=[float(value) for value in centroid],
                    bbox={'min': bbox_min.tolist(), 'max': bbox_max.tolist()},
                    extent=extent.tolist(),
                    segment='simulated',
                ),
            )
        ]

    def _simulated_lesion_enabled(self, input_path: Path) -> bool:
        checksum = zlib.crc32(input_path.read_bytes())
        return checksum % 2 == 1
