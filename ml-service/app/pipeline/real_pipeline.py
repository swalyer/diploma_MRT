import json
from pathlib import Path
import nibabel as nib
from app.adapters.totalsegmentator_adapter import TotalSegmentatorAdapter
from app.adapters.nnunet_adapter import NnUnetAdapter
from app.adapters.medsam_adapter import MedSamAdapter
from app.pipeline.base import Pipeline
from app.preprocessing.validation import validate_request
from app.preprocessing.dicom_to_nifti import to_nifti
from app.postprocessing.mask_ops import lesion_components
from app.postprocessing.mesh_generation import mask_to_glb
from app.postprocessing.report_builder import build_report
from app.schemas.infer_request import InferRequest
from app.schemas.infer_response import InferResponse, Finding


class RealPipeline(Pipeline):
    def __init__(self, artifacts_root: str, totalsegmentator: TotalSegmentatorAdapter, nnunet: NnUnetAdapter, medsam: MedSamAdapter):
        self.artifacts_root = artifacts_root
        self.totalsegmentator = totalsegmentator
        self.nnunet = nnunet
        self.medsam = medsam

    def run(self, request: InferRequest) -> InferResponse:
        validate_request(request)
        input_key = request.fileReferences['inputObjectKey']
        nii_key = to_nifti(input_key, self.artifacts_root)
        liver_mask_key, liver_real = self.totalsegmentator.segment_liver(nii_key, f'{nii_key}.liver_mask.nii.gz', self.artifacts_root, request.modality)

        experimental = request.modality == 'MRI'
        lesion_key = f'{nii_key}.lesion_mask.nii.gz'
        lesion_real = False
        if not experimental:
            lesion_key, lesion_real = self.nnunet.segment_lesion(nii_key, lesion_key, self.artifacts_root)

        lesion_data = nib.load(str(Path(self.artifacts_root) / lesion_key)).get_fdata()
        lesion_count, components = lesion_components(lesion_data)
        findings = [Finding(type='LESION', label=f'Lesion #{c["id"]}', confidence=0.7 if lesion_real else 0.3,
                            sizeMm=float(round((c['voxels'] ** (1 / 3)), 2)), volumeMm3=float(c['voxels']),
                            locationJson=json.dumps({'centroid': c['centroid']})) for c in components]

        liver_mesh = mask_to_glb(liver_mask_key, f'{nii_key}.liver.glb', self.artifacts_root)
        lesion_mesh = mask_to_glb(lesion_key, f'{nii_key}.lesion.glb', self.artifacts_root)
        report_text, report_json = build_report(request.modality, 'real', lesion_count, experimental=experimental)
        return InferResponse(
            status='COMPLETED',
            modelVersion=f"real-ts:{'on' if liver_real else 'off'}-nnunet:{'on' if lesion_real else 'off'}",
            metricsJson=json.dumps({'mode': 'real', 'liverModel': liver_real, 'lesionModel': lesion_real, 'medsamAvailable': self.medsam.interactive_available()}),
            reportText=report_text,
            reportJson=report_json,
            findings=findings,
            enhancedObjectKey=nii_key,
            liverMaskObjectKey=liver_mask_key,
            lesionMaskObjectKey=lesion_key,
            liverMeshObjectKey=liver_mesh,
            lesionMeshObjectKey=lesion_mesh,
        )
