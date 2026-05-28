"""Convert the ATLAS liver-tumor MRI dataset into nnU-Net v2 layout.

ATLAS challenge source layout (v1.0.1, flat structure):

    <atlas-root>/atlas-train-dataset-1.0.1/train/
        imagesTr/
            im0.nii.gz  im1.nii.gz  ...  im59.nii.gz
        labelsTr/
            lb0.nii.gz  lb1.nii.gz  ...  lb59.nii.gz
        dataset.json
        patient_info_train.json

Label encoding in source masks:
    0 = background
    1 = liver
    2 = tumour

Output nnU-Net v2 raw dataset:

    <out-root>/Dataset501_AtlasMRILesion/
        imagesTr/
            im0_0000.nii.gz  ...  im59_0000.nii.gz   # _0000 = channel index
        labelsTr/
            im0.nii.gz  ...  im59.nii.gz              # label name matches image stem
        dataset.json

After running this script:

    nnUNetv2_plan_and_preprocess -d 501 --verify_dataset_integrity
    nnUNetv2_train 501 3d_fullres 0

Usage:
    python scripts/prepare_atlas_dataset.py \\
        --atlas-root C:\\dataset\\atlas_raw \\
        --out-root   %nnUNet_raw% \\
        --dataset-id 501

The script auto-discovers the versioned subdirectory
(atlas-train-dataset-*) so you don't need to include it in --atlas-root.
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
_VERSIONED_PREFIX = "atlas-train-dataset-"


def _find_train_root(atlas_root: Path) -> Path:
    """Return the train/ directory, handling optional versioned subdirectory."""
    # direct: atlas_root/train/imagesTr/...
    direct = atlas_root / "train"
    if (direct / "imagesTr").exists():
        return direct
    # one-level versioned: atlas_root/atlas-train-dataset-*/train/
    for candidate in sorted(atlas_root.iterdir()):
        if candidate.is_dir() and candidate.name.startswith(_VERSIONED_PREFIX):
            versioned = candidate / "train"
            if (versioned / "imagesTr").exists():
                logger.info("Found versioned subdirectory: %s", candidate.name)
                return versioned
    return direct  # let caller fail with a clear message


def main() -> int:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument(
        "--atlas-root", type=Path, required=True,
        help="Path to extracted ATLAS dataset root (containing atlas-train-dataset-* or train/)",
    )
    parser.add_argument(
        "--out-root", type=Path, required=True,
        help="nnU-Net raw datasets root (value of $nnUNet_raw)",
    )
    parser.add_argument("--dataset-id", type=int, default=501)
    parser.add_argument("--dataset-name", default="AtlasMRILesion")
    parser.add_argument(
        "--limit", type=int, default=None,
        help="Optional cap on case count (for smoke tests)",
    )
    args = parser.parse_args()

    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")

    train_root = _find_train_root(args.atlas_root)
    images_src = train_root / "imagesTr"
    labels_src = train_root / "labelsTr"

    if not images_src.exists():
        logger.error("imagesTr not found at %s", images_src)
        return 2
    if not labels_src.exists():
        logger.error("labelsTr not found at %s", labels_src)
        return 2

    # Collect image files: im0.nii.gz, im1.nii.gz, ...
    image_files = sorted(
        images_src.glob("im*.nii.gz"),
        key=lambda p: int(p.name.replace(".nii.gz", "").lstrip("im")),
    )
    if args.limit is not None:
        image_files = image_files[: args.limit]

    if not image_files:
        logger.error("No im*.nii.gz files found in %s", images_src)
        return 3

    dataset_dir = args.out_root / DATASET_NAME_TEMPLATE.format(
        dataset_id=args.dataset_id, name=args.dataset_name
    )
    out_images = dataset_dir / "imagesTr"
    out_labels = dataset_dir / "labelsTr"
    out_images.mkdir(parents=True, exist_ok=True)
    out_labels.mkdir(parents=True, exist_ok=True)

    converted = 0
    skipped = 0
    for image_path in image_files:
        idx = image_path.name.replace(".nii.gz", "").lstrip("im")  # "0", "1", ..., "59"
        label_path = labels_src / f"lb{idx}.nii.gz"

        if not label_path.exists():
            logger.warning("Skipping im%s: label lb%s.nii.gz not found", idx, idx)
            skipped += 1
            continue

        case_id = f"im{idx}"
        dst_image = out_images / f"{case_id}_0000.nii.gz"  # nnU-Net channel suffix
        dst_label = out_labels / f"{case_id}.nii.gz"

        shutil.copyfile(image_path, dst_image)
        shutil.copyfile(label_path, dst_label)
        converted += 1

    if converted == 0:
        logger.error("No cases converted. Check paths and file naming.")
        return 3

    # nnU-Net v2 dataset.json
    # Three-class segmentation: background / liver / tumour
    dataset_json = {
        "channel_names": {"0": "T1w"},
        "labels": {
            "background": 0,
            "liver": 1,
            "tumour": 2,
        },
        "numTraining": converted,
        "file_ending": ".nii.gz",
        "dataset_name": DATASET_NAME_TEMPLATE.format(
            dataset_id=args.dataset_id, name=args.dataset_name
        ),
        "description": (
            "ATLAS challenge v1.0.1 — T1w MRI liver and liver-tumour segmentation "
            "(60 training cases, 3 classes: background / liver / tumour)"
        ),
        "reference": "https://atlas-challenge.u-bourgogne.fr/",
        "licence": "CC BY-NC-SA 4.0",
        "release": "1.0.1",
    }
    (dataset_dir / "dataset.json").write_text(json.dumps(dataset_json, indent=2))

    logger.info("Converted %d cases (%d skipped) → %s", converted, skipped, dataset_dir)
    logger.info(
        "Next: nnUNetv2_plan_and_preprocess -d %d --verify_dataset_integrity",
        args.dataset_id,
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
