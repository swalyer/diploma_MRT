from pathlib import Path
import shutil

import nibabel as nib
import numpy as np


def _ensure_3d_nifti(in_path: Path, out_path: Path) -> None:
    image = nib.load(str(in_path))
    data = image.get_fdata()
    if data.ndim <= 3:
        shutil.copyfile(in_path, out_path)
        return

    reduced = np.asarray(data[..., 0], dtype=np.float32)
    header = image.header.copy()
    header.set_data_shape(reduced.shape)
    normalized = nib.Nifti1Image(reduced, image.affine, header)
    nib.save(normalized, str(out_path))


def to_nifti(input_key: str, artifacts_root: str) -> str:
    in_path = Path(artifacts_root) / input_key
    if str(in_path).endswith(('.nii', '.nii.gz')):
        image = nib.load(str(in_path))
        if image.ndim <= 3:
            return input_key
        out_key = f"{input_key}.3d.nii.gz"
        out_path = Path(artifacts_root) / out_key
        out_path.parent.mkdir(parents=True, exist_ok=True)
        _ensure_3d_nifti(in_path, out_path)
        return out_key
    if str(in_path).endswith(('.zip', '.dcm')):
        raise ValueError('DICOM/ZIP conversion is not configured in the current runtime. Provide NIfTI input or install a DICOM-to-NIfTI converter.')
    out_key = f"{input_key}.converted.nii.gz"
    out_path = Path(artifacts_root) / out_key
    out_path.parent.mkdir(parents=True, exist_ok=True)
    _ensure_3d_nifti(in_path, out_path)
    return out_key
