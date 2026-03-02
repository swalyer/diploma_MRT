from app.schemas.infer_request import InferRequest


def validate_request(request: InferRequest) -> None:
    if 'inputObjectKey' not in request.fileReferences:
        raise ValueError('fileReferences.inputObjectKey is required')
    if request.modality not in {'CT', 'MRI'}:
        raise ValueError('Only CT and MRI modalities are supported')
