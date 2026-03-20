from pathlib import Path

import nibabel as nib
import numpy as np
from scipy import ndimage


def _normalize(data: np.ndarray) -> np.ndarray:
    finite = data[np.isfinite(data)]
    if finite.size == 0:
        return np.zeros_like(data, dtype=np.float32)
    lo = float(np.percentile(finite, 2))
    hi = float(np.percentile(finite, 98))
    if hi <= lo:
        return np.zeros_like(data, dtype=np.float32)
    clipped = np.clip(data, lo, hi)
    return ((clipped - lo) / (hi - lo)).astype(np.float32)


def _largest_component(mask: np.ndarray) -> np.ndarray:
    labeled, num = ndimage.label(mask)
    if num == 0:
        return mask.astype(bool)
    counts = np.bincount(labeled.ravel())
    counts[0] = 0
    return labeled == counts.argmax()


def _save_mask(mask: np.ndarray, reference_key: str, output_key: str, artifacts_root: str) -> str:
    ref_path = Path(artifacts_root) / reference_key
    out_path = Path(artifacts_root) / output_key
    out_path.parent.mkdir(parents=True, exist_ok=True)
    reference = nib.load(str(ref_path))
    image = nib.Nifti1Image(mask.astype(np.uint8), reference.affine, reference.header)
    nib.save(image, str(out_path))
    return output_key


def save_heuristic_liver_mask(input_key: str, output_key: str, artifacts_root: str, modality: str) -> tuple[str, bool]:
    volume = nib.load(str(Path(artifacts_root) / input_key)).get_fdata().astype(np.float32)
    norm = _normalize(volume)
    shape = np.array(norm.shape, dtype=np.float32)
    grid = np.indices(norm.shape, dtype=np.float32)

    center = np.array([shape[0] * 0.58, shape[1] * 0.52, shape[2] * 0.50], dtype=np.float32)
    radii = np.array([max(shape[0] * 0.22, 3.0), max(shape[1] * 0.26, 3.0), max(shape[2] * 0.34, 3.0)], dtype=np.float32)
    ellipsoid = (((grid[0] - center[0]) / radii[0]) ** 2 +
                 ((grid[1] - center[1]) / radii[1]) ** 2 +
                 ((grid[2] - center[2]) / radii[2]) ** 2) <= 1.0

    intensity_gate = norm > (0.35 if modality == 'MRI' else 0.20)
    candidate = ndimage.binary_closing(ellipsoid & intensity_gate, iterations=2)
    candidate = ndimage.binary_fill_holes(candidate)
    candidate = _largest_component(candidate)
    if candidate.sum() == 0:
        candidate = ellipsoid
    return _save_mask(candidate, input_key, output_key, artifacts_root), False


def save_heuristic_lesion_mask(input_key: str, liver_mask_key: str, output_key: str, artifacts_root: str, modality: str) -> tuple[str, bool]:
    volume = nib.load(str(Path(artifacts_root) / input_key)).get_fdata().astype(np.float32)
    liver_mask = nib.load(str(Path(artifacts_root) / liver_mask_key)).get_fdata() > 0
    if liver_mask.sum() == 0:
        empty = np.zeros_like(volume, dtype=np.uint8)
        return _save_mask(empty, input_key, output_key, artifacts_root), False

    norm = _normalize(volume)
    liver_values = norm[liver_mask]
    mean = float(liver_values.mean())
    std = float(liver_values.std())
    if std < 1e-6:
        std = 0.05

    high_z = 1.1 if modality == 'MRI' else 1.4
    low_z = 1.2 if modality == 'MRI' else 1.0
    hyper = norm > (mean + high_z * std)
    hypo = norm < (mean - low_z * std)
    candidate = liver_mask & (hyper | hypo)

    # Remove thin shell artifacts and keep clinically meaningful connected components only.
    candidate = ndimage.binary_opening(candidate, iterations=1)
    candidate = ndimage.binary_closing(candidate, iterations=2)
    labeled, num = ndimage.label(candidate)
    filtered = np.zeros_like(candidate, dtype=bool)
    min_voxels = max(int(liver_mask.sum() * 0.0005), 24)
    for idx in range(1, num + 1):
        component = labeled == idx
        if int(component.sum()) >= min_voxels:
            filtered |= component

    return _save_mask(filtered, input_key, output_key, artifacts_root), False
