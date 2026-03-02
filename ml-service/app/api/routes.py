from fastapi import APIRouter
from app.schemas.infer import InferRequest, InferResponse
from app.services.inference_service import InferenceService

router = APIRouter()
service = InferenceService()

@router.post('/infer/case', response_model=InferResponse)
def infer_case(request: InferRequest) -> InferResponse:
    return service.infer(request)
