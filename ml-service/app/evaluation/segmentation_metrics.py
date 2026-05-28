"""Segmentation metrics shared by every evaluator (heuristic, nnU-Net, MedSAM).

All metrics operate on binary 3D masks. Spacing-aware metrics (volume, HD95)
expect physical voxel sizes from the NIfTI header.
"""
from __future__ import annotations

from dataclasses import dataclass

import numpy as np
from scipy import ndimage


@dataclass(frozen=True)
class CaseMetrics:
    case_id: str
    dice: float
    sensitivity_per_lesion: float
    fp_per_case: float
    hd95_mm: float | None
    pred_lesion_count: int
    gt_lesion_count: int

    def as_row(self) -> dict[str, object]:
        return {
            "case_id": self.case_id,
            "dice": round(self.dice, 4),
            "sensitivity_per_lesion": round(self.sensitivity_per_lesion, 4),
            "fp_per_case": self.fp_per_case,
            "hd95_mm": None if self.hd95_mm is None else round(self.hd95_mm, 2),
            "pred_lesion_count": self.pred_lesion_count,
            "gt_lesion_count": self.gt_lesion_count,
        }


def evaluate_case(case_id: str, prediction: np.ndarray, ground_truth: np.ndarray,
                  spacing_mm: tuple[float, float, float], iou_match_threshold: float = 0.1) -> CaseMetrics:
    pred = prediction.astype(bool)
    gt = ground_truth.astype(bool)

    dice = _dice(pred, gt)
    sensitivity, fp_count, gt_count, pred_count = _lesion_level_match(pred, gt, iou_match_threshold)
    hd95 = _hd95(pred, gt, spacing_mm) if pred.any() and gt.any() else None
    return CaseMetrics(
        case_id=case_id,
        dice=dice,
        sensitivity_per_lesion=sensitivity,
        fp_per_case=float(fp_count),
        hd95_mm=hd95,
        pred_lesion_count=pred_count,
        gt_lesion_count=gt_count,
    )


def aggregate(rows: list[CaseMetrics]) -> dict[str, float]:
    if not rows:
        return {"cases": 0}
    dices = np.array([row.dice for row in rows])
    sens = np.array([row.sensitivity_per_lesion for row in rows])
    fps = np.array([row.fp_per_case for row in rows])
    hd95s = np.array([row.hd95_mm for row in rows if row.hd95_mm is not None])
    return {
        "cases": len(rows),
        "dice_mean": float(dices.mean()),
        "dice_median": float(np.median(dices)),
        "dice_std": float(dices.std()),
        "sensitivity_mean": float(sens.mean()),
        "fp_mean": float(fps.mean()),
        "hd95_mm_mean": float(hd95s.mean()) if hd95s.size else None,
    }


def _dice(pred: np.ndarray, gt: np.ndarray) -> float:
    pred_sum = int(pred.sum())
    gt_sum = int(gt.sum())
    if pred_sum == 0 and gt_sum == 0:
        return 1.0
    intersection = int(np.logical_and(pred, gt).sum())
    return 2.0 * intersection / (pred_sum + gt_sum)


def _lesion_level_match(pred: np.ndarray, gt: np.ndarray, iou_threshold: float) -> tuple[float, int, int, int]:
    pred_labels, pred_count = ndimage.label(pred)
    gt_labels, gt_count = ndimage.label(gt)
    if gt_count == 0:
        sensitivity = 1.0 if pred_count == 0 else 0.0
        return sensitivity, pred_count, 0, pred_count

    matched_gt = set()
    matched_pred = set()
    for gt_idx in range(1, gt_count + 1):
        gt_blob = gt_labels == gt_idx
        best_iou = 0.0
        best_pred_idx = None
        for pred_idx in range(1, pred_count + 1):
            if pred_idx in matched_pred:
                continue
            pred_blob = pred_labels == pred_idx
            inter = int(np.logical_and(gt_blob, pred_blob).sum())
            if inter == 0:
                continue
            union = int(np.logical_or(gt_blob, pred_blob).sum())
            iou = inter / union if union else 0.0
            if iou > best_iou:
                best_iou = iou
                best_pred_idx = pred_idx
        if best_pred_idx is not None and best_iou >= iou_threshold:
            matched_gt.add(gt_idx)
            matched_pred.add(best_pred_idx)

    sensitivity = len(matched_gt) / gt_count
    fp_count = pred_count - len(matched_pred)
    return sensitivity, max(fp_count, 0), gt_count, pred_count


def _hd95(pred: np.ndarray, gt: np.ndarray, spacing_mm: tuple[float, float, float]) -> float:
    pred_surface = _surface_voxels(pred)
    gt_surface = _surface_voxels(gt)
    if not pred_surface.any() or not gt_surface.any():
        return float("nan")
    pred_to_gt = ndimage.distance_transform_edt(~gt_surface, sampling=spacing_mm)[pred_surface]
    gt_to_pred = ndimage.distance_transform_edt(~pred_surface, sampling=spacing_mm)[gt_surface]
    distances = np.concatenate([pred_to_gt, gt_to_pred])
    return float(np.percentile(distances, 95))


def _surface_voxels(mask: np.ndarray) -> np.ndarray:
    if not mask.any():
        return np.zeros_like(mask, dtype=bool)
    eroded = ndimage.binary_erosion(mask, iterations=1)
    return np.logical_and(mask, ~eroded)
