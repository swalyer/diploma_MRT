from fastapi import APIRouter
from app.schemas.capabilities_response import CapabilitiesResponse
from app.schemas.health_response import HealthResponse
from app.schemas.infer_request import InferRequest
from app.schemas.infer_response import InferResponse
from app.services.inference_service import InferenceService

router = APIRouter()
service = InferenceService()


@router.post('/infer/case', response_model=InferResponse)
def infer_case(request: InferRequest) -> InferResponse:
    return service.infer(request)


@router.post('/v1/infer/case', response_model=InferResponse)
def infer_case_v1(request: InferRequest) -> InferResponse:
    return service.infer(request)


@router.get('/health', response_model=HealthResponse)
def health() -> HealthResponse:
    return service.health()


@router.get('/capabilities', response_model=CapabilitiesResponse)
def capabilities() -> CapabilitiesResponse:
    return service.capabilities()
