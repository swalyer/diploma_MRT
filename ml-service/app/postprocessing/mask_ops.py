import numpy as np
from scipy import ndimage


def lesion_components(mask: np.ndarray) -> tuple[int, list[dict]]:
    labeled, num = ndimage.label(mask > 0)
    out = []
    for idx in range(1, num + 1):
        coords = np.argwhere(labeled == idx)
        voxels = int(coords.shape[0])
        centroid = coords.mean(axis=0).tolist()
        out.append({'id': idx, 'voxels': voxels, 'centroid': centroid})
    return num, out
