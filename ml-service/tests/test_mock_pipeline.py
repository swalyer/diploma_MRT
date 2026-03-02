from app.pipeline.mock_pipeline import MockPipeline
from app.schemas.infer_request import InferRequest


def test_mock_pipeline_returns_artifacts():
    response = MockPipeline().run(InferRequest(caseId=1, modality='CT', executionMode='mock', fileReferences={'inputObjectKey': 'cases/1/input.nii.gz'}))
    assert response.status == 'COMPLETED'
    assert response.liverMeshObjectKey.endswith('.glb')
