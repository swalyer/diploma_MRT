from typing import Literal
from pydantic import AliasChoices, BaseModel, Field


class FileReferences(BaseModel):
    inputObjectKey: str = Field(validation_alias=AliasChoices('inputObjectKey', 'input_object_key'))


class InferRequest(BaseModel):
    caseId: int = Field(validation_alias=AliasChoices('caseId', 'case_id'))
    modality: Literal['CT', 'MRI']
    executionMode: Literal['mock', 'real'] = Field(default='mock', validation_alias=AliasChoices('executionMode', 'execution_mode'))
    fileReferences: FileReferences = Field(validation_alias=AliasChoices('fileReferences', 'file_references'))
