"""Convert the ATLAS MRI liver tumor dataset into nnU-Net v2 layout.

Expected ATLAS source layout (after extracting the official archive):

    <atlas-root>/
        train/
            atlas_001/
                im1.nii.gz          # post-contrast T1 volume
                lesion1.nii.gz      # binary lesion mask
            atlas_002/
                ...
        test/                       # ground truth not always available

This script writes a nnU-Net v2 raw dataset:

    <out-root>/Dataset501_AtlasMRILesion/
        imagesTr/atlas_001_0000.nii.gz
        labelsTr/atlas_001.nii.gz
        dataset.json

After running this script:

    nnUNetv2_plan_and_preprocess -d 501 --verify_dataset_integrity
    nnUNetv2_train 501 3d_fullres 0..4
    nnUNetv2_find_best_configuration 501

Usage:
    python -m scripts.prepare_atlas_dataset \
        --atlas-root /data/atlas \
        --out-root  /data/nnUNet_raw \
        --dataset-id 501

The script intentionally does no image rewriting beyond what the nnU-Net
preprocessing step expects. Bias correction, resampling and normalization
happen inside `nnUNetv2_plan_and_preprocess`, not here — keeping training
data identical to what `app.preprocessing.mri_preprocessing` produces at
inference time would tie the dataset spec to runtime config and is brittle.
"""
from __future__ import annotations

import argparse
import json
import logging
import shutil
import sys
from pathlib import Path

logger = logging.getLogger(__name__)

DATASET_NAME_TEMPLATE = "Dataset{dataset_id:03d}_{name}"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--atlas-root", type=Path, required=True, help="Path to extracted ATLAS dataset root")
    parser.add_argument("--out-root", type=Path, required=True, help="nnU-Net raw datasets root (nnUNet_raw)")
    parser.add_argument("--dataset-id", type=int, default=501)
    parser.add_argument("--dataset-name", default="AtlasMRILesion")
    parser.add_argument("--image-glob", default="im*.nii.gz", help="Glob inside each case dir matching the image volume")
    parser.add_argument("--label-glob", default="lesion*.nii.gz", help="Glob inside each case dir matching the lesion mask")
    parser.add_argument("--limit", type=int, default=None, help="Optional cap on case count (for smoke runs)")
    args = parser.parse_args()

    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")

    train_root = args.atlas_root / "train"
    if not train_root.exists():
        logger.error("ATLAS train directory not found at %s", train_root)
        return 2

    dataset_dir = args.out_root / DATASET_NAME_TEMPLATE.format(dataset_id=args.dataset_id, name=args.dataset_name)
    images_dir = dataset_dir / "imagesTr"
    labels_dir = dataset_dir / "labelsTr"
    images_dir.mkdir(parents=True, exist_ok=True)
    labels_dir.mkdir(parents=True, exist_ok=True)

    case_dirs = sorted(p for p in train_root.iterdir() if p.is_dir())
    if args.limit is not None:
        case_dirs = case_dirs[: args.limit]

    converted = 0
    skipped = 0
    for case_dir in case_dirs:
        image_files = sorted(case_dir.glob(args.image_glob))
        label_files = sorted(case_dir.glob(args.label_glob))
        if not image_files or not label_files:
            logger.warning("Skipping %s: image=%s label=%s", case_dir.name, image_files, label_files)
            skipped += 1
            continue
        image_src = image_files[0]
        label_src = label_files[0]
        case_id = case_dir.name
        image_dst = images_dir / f"{case_id}_0000.nii.gz"
        label_dst = labels_dir / f"{case_id}.nii.gz"
        shutil.copyfile(image_src, image_dst)
        shutil.copyfile(label_src, label_dst)
        converted += 1

    if converted == 0:
        logger.error("No cases were converted from %s. Check --image-glob/--label-glob.", train_root)
        return 3

    dataset_json = {
        "channel_names": {"0": "T1_post_contrast"},
        "labels": {"background": 0, "lesion": 1},
        "numTraining": converted,
        "file_ending": ".nii.gz",
        "dataset_name": DATASET_NAME_TEMPLATE.format(dataset_id=args.dataset_id, name=args.dataset_name),
        "description": "ATLAS post-contrast T1 MRI liver lesion segmentation",
        "reference": "https://atlas-challenge.u-bourgogne.fr/",
        "licence": "CC BY 4.0",
        "release": "1.0",
    }
    (dataset_dir / "dataset.json").write_text(json.dumps(dataset_json, indent=2))

    logger.info("Converted %d cases (%d skipped) into %s", converted, skipped, dataset_dir)
    logger.info("Next: nnUNetv2_plan_and_preprocess -d %d --verify_dataset_integrity", args.dataset_id)
    return 0


if __name__ == "__main__":
    sys.exit(main())
