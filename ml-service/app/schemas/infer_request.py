from pydantic import AliasChoices, BaseModel, Field
from app.schemas.common import ExecutionMode, Modality


class FileReferences(BaseModel):
    inputObjectKey: str = Field(validation_alias=AliasChoices('inputObjectKey', 'input_object_key'))


class RequestMetadata(BaseModel):
    requestId: str = Field(validation_alias=AliasChoices('requestId', 'request_id'))
    runId: int = Field(validation_alias=AliasChoices('runId', 'run_id'))


class InferRequest(BaseModel):
    schemaVersion: str = Field(default='v1', validation_alias=AliasChoices('schemaVersion', 'schema_version'))
    caseId: int = Field(validation_alias=AliasChoices('caseId', 'case_id'))
    modality: Modality
    executionMode: ExecutionMode = Field(default=ExecutionMode.MOCK, validation_alias=AliasChoices('executionMode', 'execution_mode'))
    fileReferences: FileReferences = Field(validation_alias=AliasChoices('fileReferences', 'file_references'))
    requestMetadata: RequestMetadata = Field(validation_alias=AliasChoices('requestMetadata', 'request_metadata'))
