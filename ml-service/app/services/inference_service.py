from app.config import settings
from app.model_registry import ModelRegistry
from app.pipeline.mock_pipeline import MockPipeline
from app.pipeline.real_pipeline import RealPipeline
from app.adapters.totalsegmentator_adapter import TotalSegmentatorAdapter
from app.adapters.nnunet_adapter import NnUnetAdapter
from app.adapters.medsam_adapter import MedSamAdapter
from app.schemas.infer_request import InferRequest
from app.schemas.infer_response import InferResponse


class InferenceService:
    def __init__(self) -> None:
        registry = ModelRegistry(settings.models_config_path)
        self.mock_pipeline = MockPipeline()
        self.real_pipeline = RealPipeline(
            artifacts_root=settings.artifacts_root,
            totalsegmentator=TotalSegmentatorAdapter(registry.get('totalsegmentator')),
            nnunet=NnUnetAdapter(registry.get('nnunetv2')),
            medsam=MedSamAdapter(registry.get('medsam')),
        )

    def infer(self, request: InferRequest) -> InferResponse:
        mode = request.executionMode or settings.mode
        if mode == 'real':
            return self.real_pipeline.run(request)
        return self.mock_pipeline.run(request)
