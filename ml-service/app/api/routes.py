from fastapi import APIRouter
from app.schemas.infer_request import InferRequest
from app.schemas.infer_response import InferResponse
from app.services.inference_service import InferenceService

router = APIRouter()
service = InferenceService()


@router.post('/infer/case', response_model=InferResponse)
def infer_case(request: InferRequest) -> InferResponse:
    return service.infer(request)
