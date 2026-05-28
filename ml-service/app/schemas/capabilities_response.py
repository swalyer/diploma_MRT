from typing import List

from pydantic import BaseModel


class CapabilitiesResponse(BaseModel):
    schemaVersion: str
    service: str
    version: str
    modes: List[str]
    modalities: List[str]
    inputFormats: List[str]
    outputs: List[str]
    supports3dLiver: bool
    supports3dSuspiciousZoneCt: bool
    supports3dSuspiciousZoneMri: bool
