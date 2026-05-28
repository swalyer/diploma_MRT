from pathlib import Path
import logging
import os
import shutil
import subprocess
import tempfile
from app.model_registry import ModelConfig
from app.postprocessing.heuristic_segmentation import save_heuristic_lesion_mask

logger = logging.getLogger(__name__)

# nnU-Net v2 dataset ID for the trained ATLAS model.
_ATLAS_DATASET_ID = "501"
_ATLAS_CONFIG = "3d_fullres"


class NnUnetAdapter:
    def __init__(self, config: ModelConfig):
        self.config = config

    def segment_lesion(
        self,
        input_key: str,
        liver_mask_key: str,
        output_key: str,
        artifacts_root: str,
        modality: str,
    ) -> tuple[str, bool]:
        in_path = Path(artifacts_root) / input_key
        out_path = Path(artifacts_root) / output_key
        out_path.parent.mkdir(parents=True, exist_ok=True)

        if self.config.enabled and self.config.command:
            dataset_id = getattr(self.config, "dataset_id", None) or _ATLAS_DATASET_ID
            nn_config = getattr(self.config, "nn_config", None) or _ATLAS_CONFIG
            folds = getattr(self.config, "folds", None) or "0"
            try:
                result_path = self._run_nnunet(in_path, out_path, dataset_id, nn_config, folds)
                if result_path:
                    return output_key, True
                logger.warning(
                    "nnUNet produced no output, falling back to heuristic",
                    extra={"input": str(in_path)},
                )
            except (FileNotFoundError, subprocess.CalledProcessError) as exc:
                logger.warning(
                    "nnUNet inference failed, falling back to heuristic",
                    exc_info=exc,
                    extra={"command": self.config.command},
                )

        return save_heuristic_lesion_mask(input_key, liver_mask_key, output_key, artifacts_root, modality)

    def _run_nnunet(
        self,
        in_path: Path,
        out_path: Path,
        dataset_id: str,
        nn_config: str,
        folds: str,
    ) -> Path | None:
        """Run nnUNetv2_predict and copy the result to out_path."""
        with tempfile.TemporaryDirectory(prefix="nnunet_infer_") as tmp:
            tmp_in = Path(tmp) / "input"
            tmp_out = Path(tmp) / "output"
            tmp_in.mkdir()
            tmp_out.mkdir()

            # nnUNetv2_predict expects files named <case>_0000.nii.gz
            stem = in_path.name.replace(".nii.gz", "").replace(".nii", "")
            shutil.copy2(in_path, tmp_in / f"{stem}_0000.nii.gz")

            env = {**os.environ}
            cmd = [
                self.config.command,   # "nnUNetv2_predict"
                "-i", str(tmp_in),
                "-o", str(tmp_out),
                "-d", str(dataset_id),
                "-c", nn_config,
                "-f", str(folds),
                "--save_probabilities",
            ]
            subprocess.run(cmd, check=True, env=env)

            # result file keeps the case stem without _0000
            pred = tmp_out / f"{stem}.nii.gz"
            if pred.exists():
                shutil.copy2(pred, out_path)
                return out_path
        return None
