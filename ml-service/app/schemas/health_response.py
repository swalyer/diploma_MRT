from pydantic import BaseModel


class HealthResponse(BaseModel):
    status: str
    mode: str
    version: str
    liverModelConfigured: bool
    lesionModelConfigured: bool
    mriHeuristicSupported: bool
