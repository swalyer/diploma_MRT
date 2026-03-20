from pathlib import Path
import logging
import shutil
import subprocess
from app.model_registry import ModelConfig
from app.postprocessing.heuristic_segmentation import save_heuristic_liver_mask

logger = logging.getLogger(__name__)


class TotalSegmentatorAdapter:
    def __init__(self, config: ModelConfig):
        self.config = config

    def segment_liver(self, nifti_key: str, output_key: str, artifacts_root: str, modality: str) -> tuple[str, bool]:
        in_path = Path(artifacts_root) / nifti_key
        out_path = Path(artifacts_root) / output_key
        out_path.parent.mkdir(parents=True, exist_ok=True)
        task = 'total_mr' if modality == 'MRI' else 'total'
        if self.config.enabled and self.config.command:
            cmd = [self.config.command, '-i', str(in_path), '-o', str(out_path.parent), '--task', task, '--fast']
            try:
                subprocess.run(cmd, check=True)
                liver_mask = out_path.parent / 'liver.nii.gz'
                if liver_mask.exists():
                    shutil.copyfile(liver_mask, out_path)
                    return output_key, True
                logger.warning("TotalSegmentator completed without liver output, falling back to heuristic mask", extra={"command": cmd})
            except (FileNotFoundError, subprocess.CalledProcessError) as exc:
                logger.warning("TotalSegmentator unavailable, falling back to heuristic liver mask", exc_info=exc, extra={"command": cmd})
        return save_heuristic_liver_mask(nifti_key, output_key, artifacts_root, modality)
