"""Run the real nnU-Net adapter on one ATLAS case and report tumour-only Dice.

Proves the label-aware lesion extraction end to end: the 3-class ATLAS model
(0 bg / 1 liver / 2 tumour) must yield a tumour-only mask, not the whole liver.

Guarded by ``__main__`` so nnU-Net's (Windows spawn) worker processes that
re-import this module do not re-execute it.

Usage:
    python scripts/verify_real_inference.py <case> --model <trainer_dir> --raw <Dataset_raw_dir>
"""
from __future__ import annotations

import argparse
import shutil
import sys
import tempfile
from pathlib import Path

import nibabel as nib
import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from app.adapters.nnunet_adapter import NnUnetAdapter, NnUnetAdapterConfig
from app.evaluation.segmentation_metrics import evaluate_case


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("case", help="ATLAS case stem, e.g. im28")
    parser.add_argument("--model", required=True, help="nnU-Net trainer dir (parent of fold_0)")
    parser.add_argument("--raw", required=True, help="Dataset raw dir with imagesTr/ and labelsTr/")
    parser.add_argument("--lesion-label", type=int, default=2)
    parser.add_argument("--device", default="cuda")
    args = parser.parse_args()

    raw = Path(args.raw)
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        key = f"{args.case}.nii.gz"
        shutil.copy2(raw / "imagesTr" / f"{args.case}_0000.nii.gz", root / key)

        adapter = NnUnetAdapter(NnUnetAdapterConfig(
            device_preference=args.device,
            model_dir_by_modality={"MRI": args.model},
            lesion_label=args.lesion_label,
        ))
        res = adapter.segment_lesion(key, key, f"{args.case}.lesion.nii.gz", str(root), "MRI")
        print(f"is_model={res.is_model} model={res.model_name} device={res.device}")
        if not res.is_model:
            print("FAIL: real model did not run (heuristic fallback)")
            return 1

        pred = np.asarray(nib.load(str(root / res.object_key)).get_fdata() > 0, dtype=bool)
        gt_img = nib.load(str(raw / "labelsTr" / f"{args.case}.nii.gz"))
        gt = gt_img.get_fdata()
        tumour_gt = np.asarray(gt == args.lesion_label, dtype=bool)
        spacing = tuple(float(v) for v in gt_img.header.get_zooms()[:3])

        m = evaluate_case(args.case, pred, tumour_gt, spacing)
        ratio = pred.sum() / max(1, int((gt > 0).sum()))
        print(f"pred_tumour_voxels={int(pred.sum())} gt_tumour_voxels={int(tumour_gt.sum())} gt_foreground={int((gt > 0).sum())}")
        print(f"tumour_Dice={m.dice:.3f} sensitivity={m.sensitivity_per_lesion:.2f} FP_per_case={m.fp_per_case:.0f} HD95mm={m.hd95_mm}")
        print(f"pred/foreground_ratio={ratio:.2f} (small => tumour-only, ~1.0 => mislabeled whole liver)")
        return 0


if __name__ == "__main__":
    sys.exit(main())
