from pathlib import Path
import shutil
import subprocess
from app.model_registry import ModelConfig


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
            subprocess.run(cmd, check=True)
            liver_mask = out_path.parent / 'liver.nii.gz'
            if liver_mask.exists():
                shutil.copyfile(liver_mask, out_path)
                return output_key, True
        shutil.copyfile(in_path, out_path)
        return output_key, False
