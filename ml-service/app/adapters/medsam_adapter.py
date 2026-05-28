from app.model_registry import ModelConfig
from app.postprocessing.heuristic_segmentation import save_heuristic_lesion_mask


class MedSamAdapter:
    def __init__(self, config: ModelConfig):
        self.config = config

    def interactive_available(self) -> bool:
        return self.config.enabled

    def segment_lesion(self, input_key: str, liver_mask_key: str, output_key: str, artifacts_root: str, modality: str) -> tuple[str, bool]:
        return save_heuristic_lesion_mask(input_key, liver_mask_key, output_key, artifacts_root, modality)
