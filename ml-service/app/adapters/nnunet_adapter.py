from pathlib import Path
import logging
import shutil
import subprocess
from app.model_registry import ModelConfig
from app.postprocessing.heuristic_segmentation import save_heuristic_lesion_mask

logger = logging.getLogger(__name__)


class NnUnetAdapter:
    def __init__(self, config: ModelConfig):
        self.config = config

    def segment_lesion(self, input_key: str, liver_mask_key: str, output_key: str, artifacts_root: str, modality: str) -> tuple[str, bool]:
        in_path = Path(artifacts_root) / input_key
        out_path = Path(artifacts_root) / output_key
        out_path.parent.mkdir(parents=True, exist_ok=True)
        if self.config.enabled and self.config.command and self.config.weights:
            cmd = [self.config.command, '-i', str(in_path.parent), '-o', str(out_path.parent), '-d', self.config.weights]
            try:
                subprocess.run(cmd, check=True)
                pred = out_path.parent / in_path.name
                if pred.exists():
                    shutil.copyfile(pred, out_path)
                    return output_key, True
                logger.warning("nnUNet completed without prediction output, falling back to heuristic lesion mask", extra={"command": cmd})
            except (FileNotFoundError, subprocess.CalledProcessError) as exc:
                logger.warning("nnUNet unavailable, falling back to heuristic lesion mask", exc_info=exc, extra={"command": cmd})
        return save_heuristic_lesion_mask(input_key, liver_mask_key, output_key, artifacts_root, modality)
