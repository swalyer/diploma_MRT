"""MRI-specific preprocessing: N4 bias correction, isotropic resample, z-score normalize.

These steps are mandatory before MRI lesion segmentation. CT volumes are
already in calibrated Hounsfield units, so they skip this stage entirely.
"""
from __future__ import annotations

import logging
from dataclasses import dataclass
from pathlib import Path

import nibabel as nib
import numpy as np

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class MriPreprocessingConfig:
    target_spacing_mm: tuple[float, float, float] = (1.0, 1.0, 1.0)
    n4_shrink_factor: int = 4
    n4_max_iterations: tuple[int, ...] = (50, 50, 30, 20)
    zscore_clip: tuple[float, float] = (-5.0, 5.0)
    enable_n4: bool = True


def preprocess_mri_volume(input_key: str, output_key: str, artifacts_root: str,
                          config: MriPreprocessingConfig | None = None) -> str:
    """N4 bias correction → isotropic resample → z-score normalize.

    Returns the storage key of the preprocessed volume. Falls back gracefully
    when SimpleITK is unavailable (skips N4 only) so the pipeline keeps working
    in lightweight smoke environments.
    """
    cfg = config or MriPreprocessingConfig()
    in_path = Path(artifacts_root) / input_key
    out_path = Path(artifacts_root) / output_key
    out_path.parent.mkdir(parents=True, exist_ok=True)

    image = nib.load(str(in_path))
    data = np.asarray(image.get_fdata(), dtype=np.float32)
    affine = image.affine.copy()
    spacing = tuple(float(value) for value in image.header.get_zooms()[:3])

    if cfg.enable_n4:
        data = _apply_n4_bias_correction(data, spacing, cfg)

    data, affine, spacing = _resample_isotropic(data, affine, spacing, cfg.target_spacing_mm)
    data = _zscore_normalize(data, cfg.zscore_clip)

    new_image = nib.Nifti1Image(data.astype(np.float32), affine)
    new_image.header.set_zooms(spacing)
    nib.save(new_image, str(out_path))
    return output_key


def _apply_n4_bias_correction(data: np.ndarray, spacing: tuple[float, float, float],
                              cfg: MriPreprocessingConfig) -> np.ndarray:
    try:
        import SimpleITK as sitk
    except ImportError:
        logger.warning("SimpleITK not installed, skipping N4 bias correction")
        return data

    sitk_image = sitk.GetImageFromArray(np.transpose(data, (2, 1, 0)))
    sitk_image.SetSpacing(spacing)
    sitk_image = sitk.Cast(sitk_image, sitk.sitkFloat32)

    mask = sitk.OtsuThreshold(sitk_image, 0, 1, 200)
    shrunk = sitk.Shrink(sitk_image, [cfg.n4_shrink_factor] * 3)
    shrunk_mask = sitk.Shrink(mask, [cfg.n4_shrink_factor] * 3)

    corrector = sitk.N4BiasFieldCorrectionImageFilter()
    corrector.SetMaximumNumberOfIterations(list(cfg.n4_max_iterations))
    try:
        corrector.Execute(shrunk, shrunk_mask)
    except RuntimeError as exc:
        logger.warning("N4 bias correction failed, returning raw volume: %s", exc)
        return data

    log_bias_field = corrector.GetLogBiasFieldAsImage(sitk_image)
    corrected = sitk_image / sitk.Exp(log_bias_field)
    corrected_array = sitk.GetArrayFromImage(corrected)
    return np.transpose(corrected_array, (2, 1, 0)).astype(np.float32)


def _resample_isotropic(data: np.ndarray, affine: np.ndarray,
                        spacing: tuple[float, float, float],
                        target_spacing: tuple[float, float, float]) -> tuple[np.ndarray, np.ndarray, tuple[float, float, float]]:
    if all(abs(s - t) < 1e-3 for s, t in zip(spacing, target_spacing)):
        return data, affine, spacing

    from scipy.ndimage import zoom

    zoom_factors = tuple(s / t for s, t in zip(spacing, target_spacing))
    resampled = zoom(data, zoom_factors, order=1, mode='nearest')

    new_affine = affine.copy()
    rotation = affine[:3, :3]
    column_norms = np.linalg.norm(rotation, axis=0)
    column_norms[column_norms == 0] = 1.0
    new_rotation = rotation / column_norms * np.array(target_spacing)
    new_affine[:3, :3] = new_rotation
    return resampled.astype(np.float32), new_affine, target_spacing


def _zscore_normalize(data: np.ndarray, clip_range: tuple[float, float]) -> np.ndarray:
    finite = data[np.isfinite(data)]
    if finite.size == 0:
        return np.zeros_like(data, dtype=np.float32)
    nonzero = finite[finite > 0]
    if nonzero.size > 0:
        mean = float(nonzero.mean())
        std = float(nonzero.std())
    else:
        mean = float(finite.mean())
        std = float(finite.std())
    if std < 1e-6:
        std = 1.0
    normalized = (data - mean) / std
    return np.clip(normalized, clip_range[0], clip_range[1]).astype(np.float32)
