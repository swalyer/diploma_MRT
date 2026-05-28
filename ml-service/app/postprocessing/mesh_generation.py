from pathlib import Path
import numpy as np
import nibabel as nib
from skimage import measure
import trimesh


def mask_to_glb(mask_key: str, output_key: str, artifacts_root: str) -> str:
    mask_path = Path(artifacts_root) / mask_key
    out_path = Path(artifacts_root) / output_key
    out_path.parent.mkdir(parents=True, exist_ok=True)

    data = nib.load(str(mask_path)).get_fdata().astype(np.uint8)
    if data.max() == 0:
        trimesh.creation.icosphere(subdivisions=1, radius=0.01).export(str(out_path))
        return output_key
    verts, faces, _, _ = measure.marching_cubes(data, level=0.5)
    mesh = trimesh.Trimesh(vertices=verts, faces=faces)
    mesh.export(str(out_path))
    return output_key
