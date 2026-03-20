import numpy as np
from scipy import ndimage


def lesion_components(mask: np.ndarray) -> tuple[int, list[dict]]:
    labeled, num = ndimage.label(mask > 0)
    out = []
    for idx in range(1, num + 1):
        coords = np.argwhere(labeled == idx)
        voxels = int(coords.shape[0])
        centroid = coords.mean(axis=0).tolist()
        mins = coords.min(axis=0).tolist()
        maxs = coords.max(axis=0).tolist()
        extent = (coords.max(axis=0) - coords.min(axis=0) + 1).tolist()
        out.append({'id': idx, 'voxels': voxels, 'centroid': centroid, 'bbox': {'min': mins, 'max': maxs}, 'extent': extent})
    return num, out
