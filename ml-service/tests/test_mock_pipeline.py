import nibabel as nib
import numpy as np

from app.pipeline.mock_pipeline import MockPipeline
from app.schemas.common import ExecutionMode, Modality
from app.schemas.infer_request import InferRequest


def test_mock_pipeline_returns_artifacts(tmp_path):
    artifacts_root = tmp_path
    input_key = "cases/1/input.nii.gz"
    input_path = artifacts_root / input_key
    input_path.parent.mkdir(parents=True, exist_ok=True)
    nib.save(nib.Nifti1Image(np.ones((8, 8, 8), dtype=np.float32), np.eye(4)), str(input_path))

    response = MockPipeline(str(artifacts_root)).run(
        InferRequest(
            caseId=1,
            modality=Modality.CT,
            executionMode=ExecutionMode.MOCK,
            fileReferences={'inputObjectKey': input_key},
            requestMetadata={'requestId': 'test-request-1', 'runId': 1},
        )
    )
    assert response.status == 'COMPLETED'
    assert response.artifacts.liverMeshObjectKey.endswith('.glb')
    assert (artifacts_root / response.artifacts.enhancedObjectKey).exists()
    assert (artifacts_root / response.artifacts.liverMaskObjectKey).exists()
    assert (artifacts_root / response.artifacts.liverMeshObjectKey).exists()
