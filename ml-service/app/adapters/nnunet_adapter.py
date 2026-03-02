from pathlib import Path
import shutil
import subprocess
from app.model_registry import ModelConfig


class NnUnetAdapter:
    def __init__(self, config: ModelConfig):
        self.config = config

    def segment_lesion(self, input_key: str, output_key: str, artifacts_root: str) -> tuple[str, bool]:
        in_path = Path(artifacts_root) / input_key
        out_path = Path(artifacts_root) / output_key
        out_path.parent.mkdir(parents=True, exist_ok=True)
        if self.config.enabled and self.config.command and self.config.weights:
            cmd = [self.config.command, '-i', str(in_path.parent), '-o', str(out_path.parent), '-d', self.config.weights]
            subprocess.run(cmd, check=True)
            pred = out_path.parent / in_path.name
            if pred.exists():
                shutil.copyfile(pred, out_path)
                return output_key, True
        shutil.copyfile(in_path, out_path)
        return output_key, False
