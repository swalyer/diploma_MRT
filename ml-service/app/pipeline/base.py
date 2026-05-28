from abc import ABC, abstractmethod
from app.schemas.infer_request import InferRequest
from app.schemas.infer_response import InferResponse


class Pipeline(ABC):
    @abstractmethod
    def run(self, request: InferRequest) -> InferResponse:
        raise NotImplementedError
