from pydantic import BaseModel
from typing import Dict


class InferRequest(BaseModel):
    caseId: int
    modality: str
    executionMode: str = 'mock'
    fileReferences: Dict[str, str]
