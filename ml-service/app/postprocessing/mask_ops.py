import math

import numpy as np
from scipy import ndimage


def lesion_components(mask: np.ndarray, evidence: np.ndarray | None = None) -> tuple[int, list[dict]]:
    """Extract connected lesion components with optional confidence.

    When ``evidence`` is provided (per-voxel score, e.g. |z-score| for the
    heuristic or softmax probability for a model), each component gets a
    ``confidence`` field in [0, 1]. Otherwise ``confidence`` is None.
    """
    labeled, num = ndimage.label(mask > 0)
    out: list[dict] = []
    for idx in range(1, num + 1):
        coords = np.argwhere(labeled == idx)
        voxels = int(coords.shape[0])
        centroid = coords.mean(axis=0).tolist()
        mins = coords.min(axis=0).tolist()
        maxs = coords.max(axis=0).tolist()
        extent = (coords.max(axis=0) - coords.min(axis=0) + 1).tolist()
        component = {
            'id': idx,
            'voxels': voxels,
            'centroid': centroid,
            'bbox': {'min': mins, 'max': maxs},
            'extent': extent,
            'confidence': None,
        }
        if evidence is not None:
            component['confidence'] = _component_confidence(evidence[labeled == idx], voxels)
        out.append(component)
    return num, out


def _component_confidence(component_evidence: np.ndarray, voxels: int) -> float:
    """Combine evidence amplitude with size into [0, 1] confidence.

    Heuristic intuition: a tumor candidate is more credible when its peak
    z-score is large AND the lesion is reasonably sized. Tiny single-voxel
    spikes get penalized; broad coherent regions are amplified.

    For model-derived evidence (softmax) the amplitude is already in [0, 1],
    so the size factor only nudges confidence up for sustained predictions.
    """
    finite = component_evidence[np.isfinite(component_evidence)]
    if finite.size == 0:
        return 0.0
    peak = float(np.percentile(finite, 90))
    amplitude = _normalize_amplitude(peak)
    size_factor = 1.0 - math.exp(-voxels / 64.0)
    return float(np.clip(amplitude * size_factor, 0.0, 1.0))


def _normalize_amplitude(peak: float) -> float:
    if peak <= 1.0:
        # Already model-style probability.
        return float(np.clip(peak, 0.0, 1.0))
    # Heuristic |z-score| amplitude — squash via logistic centred at z=2 (~p<0.05).
    return float(1.0 / (1.0 + math.exp(-(peak - 2.0))))
