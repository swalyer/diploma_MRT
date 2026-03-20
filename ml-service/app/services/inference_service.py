import logging

from app.config import settings
from app.model_registry import ModelRegistry
from app.pipeline.mock_pipeline import MockPipeline
from app.pipeline.real_pipeline import RealPipeline
from app.adapters.totalsegmentator_adapter import TotalSegmentatorAdapter
from app.adapters.nnunet_adapter import NnUnetAdapter
from app.adapters.medsam_adapter import MedSamAdapter
from app.schemas.capabilities_response import CapabilitiesResponse
from app.schemas.health_response import HealthResponse
from app.schemas.infer_request import InferRequest
from app.schemas.infer_response import InferResponse

logger = logging.getLogger(__name__)


class InferenceService:
    def __init__(self) -> None:
        registry = ModelRegistry(settings.models_config_path)
        self.registry = registry
        self.mock_pipeline = MockPipeline(settings.artifacts_root)
        self.real_pipeline = RealPipeline(
            artifacts_root=settings.artifacts_root,
            totalsegmentator=TotalSegmentatorAdapter(registry.get('totalsegmentator')),
            nnunet=NnUnetAdapter(registry.get('nnunetv2')),
            medsam=MedSamAdapter(registry.get('medsam')),
        )

    def infer(self, request: InferRequest) -> InferResponse:
        logger.info(
            "ml inference requested schema=%s requestId=%s caseId=%s runId=%s modality=%s mode=%s",
            request.schemaVersion,
            request.requestMetadata.requestId,
            request.caseId,
            request.requestMetadata.runId,
            request.modality,
            request.executionMode,
        )
        mode = request.executionMode or settings.mode
        if mode == 'real':
            return self.real_pipeline.run(request)
        return self.mock_pipeline.run(request)

    def health(self) -> HealthResponse:
        total_segmentator = self.registry.get('totalsegmentator')
        nnunet = self.registry.get('nnunetv2')
        return HealthResponse(
            status='UP',
            mode=settings.mode,
            version='ml-service-v1',
            liverModelConfigured=bool(total_segmentator.enabled and total_segmentator.command),
            lesionModelConfigured=bool(nnunet.enabled and nnunet.command and nnunet.weights),
            mriHeuristicSupported=True,
        )

    def capabilities(self) -> CapabilitiesResponse:
        return CapabilitiesResponse(
            schemaVersion='v1',
            service='ml-service',
            version='ml-service-v1',
            modes=['mock', 'real'],
            modalities=['CT', 'MRI'],
            inputFormats=['nii', 'nii.gz'],
            outputs=['enhancedVolume', 'liverMask', 'lesionMask', 'liverMesh', 'lesionMesh', 'findings', 'report'],
            supports3dLiver=True,
            supports3dSuspiciousZoneCt=True,
            supports3dSuspiciousZoneMri=True,
        )
