#!/usr/bin/env bash
set -euo pipefail
mkdir -p models
cat <<EOF
Download model weights manually or via provider scripts:
- TotalSegmentator auto-downloads on first run.
- nnUNet v2: place trained weights under ./models/nnunet and set ML_MODELS_CONFIG_PATH.
- MedSAM (optional): place checkpoint under ./models/medsam.
EOF
