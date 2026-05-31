from pathlib import Path
import numpy as np
import nibabel as nib
from skimage import measure
import trimesh


def mask_to_glb(mask_key: str, output_key: str, artifacts_root: str) -> str:
    """Marching-cubes mesh in anatomically correct physical space.

    Vertices come out of marching cubes in voxel index coordinates; we map them
    through the NIfTI affine so the mesh carries real millimetre spacing AND the
    scan's RAS orientation. Without this, anisotropic spacing (e.g. 1.25×1.25×3.5
    mm) squashes the mesh along the slice axis and the anatomy looks wrong.

    Liver and lesion masks share the source volume's affine and shape, so both
    meshes are transformed identically — they stay co-registered — and are
    recentred on the shared volume centre so the existing 3D camera frames them.
    """
    mask_path = Path(artifacts_root) / mask_key
    out_path = Path(artifacts_root) / output_key
    out_path.parent.mkdir(parents=True, exist_ok=True)

    image = nib.load(str(mask_path))
    data = np.asarray(image.get_fdata()).astype(np.uint8)
    if data.max() == 0:
        trimesh.creation.icosphere(subdivisions=1, radius=0.01).export(str(out_path))
        return output_key

    verts, faces, _, _ = measure.marching_cubes(data, level=0.5)
    affine = image.affine
    verts_world = nib.affines.apply_affine(affine, verts)
    # Recentre on the volume centre (same for liver & lesion → co-registered),
    # so the scene sits near the origin for the viewer's fixed camera.
    volume_center = nib.affines.apply_affine(affine, np.array(data.shape, dtype=float) / 2.0)
    verts_world = verts_world - volume_center

    mesh = trimesh.Trimesh(vertices=verts_world, faces=faces)
    mesh.export(str(out_path))
    return output_key
