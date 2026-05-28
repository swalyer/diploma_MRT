from enum import Enum


class Modality(str, Enum):
    CT = 'CT'
    MRI = 'MRI'


class ExecutionMode(str, Enum):
    MOCK = 'mock'
    REAL = 'real'


class FindingType(str, Enum):
    LESION = 'LESION'
