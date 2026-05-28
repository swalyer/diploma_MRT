from app.schemas.infer_request import InferRequest


def validate_request(request: InferRequest) -> None:
    if not request.fileReferences.inputObjectKey:
        raise ValueError('fileReferences.inputObjectKey is required')
