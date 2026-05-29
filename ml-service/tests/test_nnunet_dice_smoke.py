"""GPU/weights-gated Dice smoke test for the real nnU-Net lesion path (FR-3).

This is the regression gate that proves the trained model still clears the
thesis bar (Dice >= 0.45) on a held-out case. It is intentionally *skipped*
unless a CUDA device, a trained model, and a labeled case are all present, so
CI (no GPU/weights) and ordinary local runs stay green. On the training
machine, point it at the model + a validation case and it runs for real.

Enable by exporting (PowerShell ``$env:`` / shell ``export``):

    ML_SMOKE_NNUNET_MODEL_DIR=<...>/nnUNet_results/Dataset501_*/nnUNetTrainer__nnUNetPlans__3d_fullres
    ML_SMOKE_IMAGE=<...>/case_0000.nii.gz        # raw input volume
    ML_SMOKE_LABEL=<...>/case.nii.gz             # ground-truth lesion mask
    ML_SMOKE_MIN_DICE=0.45                        # optional, default 0.45
    ML_SMOKE_MODALITY=MRI                         # optional, default MRI

For full held-out-set scoring use ``scripts/evaluate_segmentation.py`` instead.
"""
from __future__ import annotations

import os
from pathlib import Path

import nibabel as nib
import numpy as np
import pytest

from app.adapters.nnunet_adapter import NnUnetAdapter, NnUnetAdapterConfig
from app.evaluation.segmentation_metrics import evaluate_case

MODEL_DIR = os.getenv("ML_SMOKE_NNUNET_MODEL_DIR")
IMAGE = os.getenv("ML_SMOKE_IMAGE")
LABEL = os.getenv("ML_SMOKE_LABEL")
MIN_DICE = float(os.getenv("ML_SMOKE_MIN_DICE", "0.45"))
MODALITY = os.getenv("ML_SMOKE_MODALITY", "MRI")


def _cuda_available() -> bool:
    try:
        import torch
        return torch.cuda.is_available()
    except Exception:
        return False


pytestmark = pytest.mark.skipif(
    not (MODEL_DIR and IMAGE and LABEL),
    reason="Set ML_SMOKE_NNUNET_MODEL_DIR / ML_SMOKE_IMAGE / ML_SMOKE_LABEL to run the real nnU-Net Dice smoke test",
)


def test_nnunet_lesion_dice_meets_threshold(tmp_path: Path):
    if not _cuda_available():
        pytest.skip("CUDA not available; real nnU-Net inference smoke test requires a GPU")
    for label, value in (("model dir", MODEL_DIR), ("image", IMAGE), ("label", LABEL)):
        if not Path(value).exists():
            pytest.skip(f"Configured {label} does not exist: {value}")

    # Stage the input under an artifacts root the way the pipeline does.
    input_key = "smoke/case.nii.gz"
    in_path = tmp_path / input_key
    in_path.parent.mkdir(parents=True, exist_ok=True)
    in_path.write_bytes(Path(IMAGE).read_bytes())

    adapter = NnUnetAdapter(
        NnUnetAdapterConfig(
            device_preference="cuda",
            model_dir_by_modality={MODALITY: MODEL_DIR},
        )
    )
    result = adapter.segment_lesion(
        input_key=input_key,
        liver_mask_key=input_key,  # unused on the real-model path
        output_key="smoke/case.lesion.nii.gz",
        artifacts_root=str(tmp_path),
        modality=MODALITY,
    )

    assert result.is_model, "Expected the real nnU-Net path to run, not the heuristic fallback"
    assert result.device == "cuda"

    pred_image = nib.load(str(tmp_path / result.object_key))
    prediction = np.asarray(pred_image.get_fdata() > 0, dtype=bool)
    gt_image = nib.load(str(LABEL))
    ground_truth = np.asarray(gt_image.get_fdata() > 0, dtype=bool)
    spacing = tuple(float(v) for v in gt_image.header.get_zooms()[:3])

    metrics = evaluate_case("smoke", prediction, ground_truth, spacing)
    print(f"nnU-Net smoke Dice={metrics.dice:.3f} sensitivity={metrics.sensitivity_per_lesion:.2f} "
          f"FP={metrics.fp_per_case:.0f} (threshold {MIN_DICE})")
    assert metrics.dice >= MIN_DICE, f"Dice {metrics.dice:.3f} below threshold {MIN_DICE}"
