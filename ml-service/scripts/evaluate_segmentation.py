"""Universal evaluator for any lesion-segmentation predictor on an ATLAS-style dataset.

Two modes:

1. ``--predictor heuristic`` — runs `app.postprocessing.heuristic_segmentation`
   on each case and computes metrics on the spot. Used to validate the
   honest baseline.

2. ``--predictor predictions-dir --predictions-dir <path>`` — assumes someone
   has already produced predictions (e.g. nnU-Net `nnUNetv2_predict` output)
   and only computes metrics. Used to score trained models without re-running
   the inference pipeline.

Expected dataset layout (matches `prepare_atlas_dataset.py` output):

    <root>/imagesTs/case_id_0000.nii.gz
    <root>/labelsTs/case_id.nii.gz

Predictions, when supplied separately, must be named `case_id.nii.gz`.

Output: JSON metrics file + per-case CSV. Aggregate row printed to stdout.
"""
from __future__ import annotations

import argparse
import csv
import json
import logging
import sys
import tempfile
from pathlib import Path

import nibabel as nib
import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.evaluation.segmentation_metrics import CaseMetrics, aggregate, evaluate_case

logger = logging.getLogger(__name__)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dataset-root", type=Path, required=True,
                        help="Root containing images*/labels* folders")
    parser.add_argument("--split", choices=["Ts", "Tr"], default="Ts",
                        help="Dataset split: 'Ts' for imagesTs/labelsTs (test), 'Tr' for imagesTr/labelsTr (train/val)")
    parser.add_argument("--predictor", choices=["heuristic", "predictions-dir"], required=True)
    parser.add_argument("--predictions-dir", type=Path, default=None,
                        help="Folder with case_id.nii.gz predictions, required when predictor=predictions-dir")
    parser.add_argument("--modality", default="MRI", help="Used by heuristic predictor")
    parser.add_argument("--output", type=Path, default=Path("./eval_report.json"))
    parser.add_argument("--csv", type=Path, default=Path("./eval_per_case.csv"))
    parser.add_argument("--limit", type=int, default=None)
    args = parser.parse_args()

    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")

    images_dir = args.dataset_root / f"images{args.split}"
    labels_dir = args.dataset_root / f"labels{args.split}"
    if not images_dir.exists() or not labels_dir.exists():
        logger.error("Expected images%s/ and labels%s/ inside %s", args.split, args.split, args.dataset_root)
        return 2

    if args.predictor == "predictions-dir" and not args.predictions_dir:
        logger.error("--predictions-dir is required when predictor=predictions-dir")
        return 2

    rows: list[CaseMetrics] = []
    image_paths = sorted(images_dir.glob("*_0000.nii.gz"))
    if args.limit:
        image_paths = image_paths[: args.limit]

    for image_path in image_paths:
        case_id = image_path.name.replace("_0000.nii.gz", "")
        label_path = labels_dir / f"{case_id}.nii.gz"
        if not label_path.exists():
            logger.warning("Missing ground truth for %s, skipping", case_id)
            continue

        gt_image = nib.load(str(label_path))
        gt = np.asarray(gt_image.get_fdata() > 0, dtype=bool)
        spacing = tuple(float(value) for value in gt_image.header.get_zooms()[:3])

        if args.predictor == "heuristic":
            prediction = _predict_heuristic(image_path, args.modality)
        else:
            pred_path = args.predictions_dir / f"{case_id}.nii.gz"
            if not pred_path.exists():
                logger.warning("Missing prediction for %s, skipping", case_id)
                continue
            prediction = np.asarray(nib.load(str(pred_path)).get_fdata() > 0, dtype=bool)

        metrics = evaluate_case(case_id, prediction, gt, spacing)
        rows.append(metrics)
        logger.info("%s — Dice=%.3f sensitivity=%.2f FP=%.0f", case_id, metrics.dice,
                    metrics.sensitivity_per_lesion, metrics.fp_per_case)

    summary = aggregate(rows)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(summary, indent=2))

    args.csv.parent.mkdir(parents=True, exist_ok=True)
    with args.csv.open("w", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=["case_id", "dice", "sensitivity_per_lesion", "fp_per_case",
                                                "hd95_mm", "pred_lesion_count", "gt_lesion_count"])
        writer.writeheader()
        writer.writerows(row.as_row() for row in rows)

    print(json.dumps(summary, indent=2))
    return 0


def _predict_heuristic(image_path: Path, modality: str) -> np.ndarray:
    from app.postprocessing.heuristic_segmentation import save_heuristic_lesion_mask, save_heuristic_liver_mask

    with tempfile.TemporaryDirectory() as tmp_root:
        artifacts_root = Path(tmp_root)
        relative = image_path.name
        target = artifacts_root / relative
        target.write_bytes(image_path.read_bytes())

        liver_key, _ = save_heuristic_liver_mask(relative, f"{relative}.liver.nii.gz", str(artifacts_root), modality)
        lesion_key, _ = save_heuristic_lesion_mask(relative, liver_key, f"{relative}.lesion.nii.gz",
                                                    str(artifacts_root), modality)
        prediction = nib.load(str(artifacts_root / lesion_key)).get_fdata() > 0
    return np.asarray(prediction, dtype=bool)


if __name__ == "__main__":
    sys.exit(main())
