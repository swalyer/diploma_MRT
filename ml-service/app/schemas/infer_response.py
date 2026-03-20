from pydantic import BaseModel
from typing import List, Optional
from app.schemas.common import ExecutionMode, FindingType, Modality


class BoundingBox(BaseModel):
    min: List[int]
    max: List[int]


class FindingLocation(BaseModel):
    segment: Optional[str] = None
    centroid: Optional[List[float]] = None
    bbox: Optional[BoundingBox] = None
    extent: Optional[List[int]] = None
    suspicion: Optional[str] = None


class Metrics(BaseModel):
    mode: ExecutionMode
    liverModel: Optional[bool] = None
    lesionModel: Optional[bool] = None
    medsamAvailable: Optional[bool] = None
    supportsMri3dSuspiciousZone: Optional[bool] = None


class ReportSections(BaseModel):
    findings: str
    impression: str
    limitations: str
    recommendation: str


class ReportCapabilities(BaseModel):
    supports3dLiver: bool
    supports3dLesion: bool


class ReportData(BaseModel):
    modality: Modality
    executionMode: ExecutionMode
    lesionCount: int
    evidenceBound: bool
    sections: ReportSections
    capabilities: ReportCapabilities


class Finding(BaseModel):
    type: FindingType
    label: str
    confidence: Optional[float] = None
    sizeMm: Optional[float] = None
    volumeMm3: Optional[float] = None
    location: FindingLocation


class ArtifactOutputs(BaseModel):
    enhancedObjectKey: str
    liverMaskObjectKey: str
    lesionMaskObjectKey: str
    liverMeshObjectKey: str
    lesionMeshObjectKey: Optional[str] = None


class InferResponse(BaseModel):
    schemaVersion: str = 'v1'
    status: str
    modelVersion: str
    metrics: Metrics
    reportText: str
    reportData: ReportData
    findings: List[Finding]
    artifacts: ArtifactOutputs
