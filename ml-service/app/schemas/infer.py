from pydantic import BaseModel
from typing import Dict, List, Optional

class InferRequest(BaseModel):
    caseId: int
    modality: str
    fileReferences: Dict[str, str]

class Finding(BaseModel):
    type: str
    label: str
    confidence: float
    sizeMm: Optional[float] = None
    volumeMm3: Optional[float] = None
    locationJson: str

class InferResponse(BaseModel):
    status: str
    modelVersion: str
    metricsJson: str
    reportText: str
    reportJson: str
    findings: List[Finding]
    enhancedObjectKey: str
    liverMaskObjectKey: str
    lesionMaskObjectKey: str
    liverMeshObjectKey: str
    lesionMeshObjectKey: str
