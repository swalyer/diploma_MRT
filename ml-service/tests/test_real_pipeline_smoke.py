from pathlib import Path
import numpy as np
import nibabel as nib
from app.pipeline.real_pipeline import RealPipeline
from app.adapters.totalsegmentator_adapter import TotalSegmentatorAdapter
from app.adapters.nnunet_adapter import NnUnetAdapter
from app.adapters.medsam_adapter import MedSamAdapter
from app.model_registry import ModelConfig
from app.schemas.infer_request import InferRequest


def test_real_pipeline_smoke(tmp_path: Path):
    arr = np.zeros((16, 16, 16), dtype=np.uint8)
    arr[4:12, 4:12, 4:12] = 1
    key = 'cases/1/input.nii.gz'
    path = tmp_path / key
    path.parent.mkdir(parents=True, exist_ok=True)
    nib.save(nib.Nifti1Image(arr, np.eye(4)), str(path))

    pipeline = RealPipeline(
        artifacts_root=str(tmp_path),
        totalsegmentator=TotalSegmentatorAdapter(ModelConfig('ts', False)),
        nnunet=NnUnetAdapter(ModelConfig('nn', False)),
        medsam=MedSamAdapter(ModelConfig('ms', False)),
    )
    res = pipeline.run(InferRequest(caseId=1, modality='CT', executionMode='real', fileReferences={'inputObjectKey': key}))
    assert res.status == 'COMPLETED'
    assert (tmp_path / res.liverMeshObjectKey).exists()
