from dataclasses import dataclass

@dataclass
class PipelineArtifacts:
    enhanced_path: str
    liver_mask_path: str
    lesion_mask_path: str
    liver_mesh_path: str
    lesion_mesh_path: str

class PreprocessStage:
    def run(self, input_path: str) -> str:
        return input_path

class EnhancementStage:
    def run(self, base_path: str) -> str:
        return f"{base_path}.enhanced.nii.gz"

class LiverSegmentationStage:
    def run(self, base_path: str) -> str:
        return f"{base_path}.liver_mask.nii.gz"

class LesionDetectionStage:
    def run(self, base_path: str) -> str:
        return f"{base_path}.lesion_mask.nii.gz"

class MeshGenerationStage:
    def run(self, base_path: str) -> tuple[str, str]:
        return f"{base_path}.liver.glb", f"{base_path}.lesion.glb"
