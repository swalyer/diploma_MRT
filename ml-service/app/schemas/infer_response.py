from pydantic import BaseModel
from typing import List, Optional


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
