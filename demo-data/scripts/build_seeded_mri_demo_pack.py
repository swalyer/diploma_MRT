from __future__ import annotations

import array
from pathlib import Path

from build_seeded_ct_demo_pack import (
    DEMO_STORAGE_ROOT,
    MANIFEST_ROOT,
    NiftiVolume,
    ROOT,
    build_demo_case,
    connected_components,
    finding_from_component,
    read_nifti_gz,
    write_bbox_cube_glb,
    write_component_mask,
    write_nifti_gz,
    write_zero_mask,
)


SOURCE_CASE_ROOT = ROOT / "storage" / "cases" / "3"


def main() -> None:
    MANIFEST_ROOT.mkdir(parents=True, exist_ok=True)
    DEMO_STORAGE_ROOT.mkdir(parents=True, exist_ok=True)

    original_source = SOURCE_CASE_ROOT / "mri-smoke.nii.gz"
    liver_mask_source = SOURCE_CASE_ROOT / "mri-smoke.nii.gz.liver_mask.nii.gz"
    liver_mesh_source = SOURCE_CASE_ROOT / "mri-smoke.nii.gz.liver.glb"

    single_root = DEMO_STORAGE_ROOT / "mri-single-lesion-001"
    derived_lesion_mask = derive_heuristic_suspicious_zone_mask(
        input_path=original_source,
        liver_mask_path=liver_mask_source,
        output_path=single_root / "lesion_mask.derived.nii.gz",
    )
    lesion_volume = read_nifti_gz(derived_lesion_mask)
    component_stats = sorted(
        connected_components(lesion_volume),
        key=lambda component: (-len(component.voxel_indices), component.component_id),
    )
    if not component_stats:
        raise RuntimeError("Expected at least one heuristic suspicious-zone component in the MRI smoke mask")

    normal_slug = "mri-normal-001"
    single_slug = "mri-single-lesion-001"

    build_demo_case(
        case_slug=normal_slug,
        modality="MRI",
        category="NORMAL",
        patient_pseudo_id="demo-mri-normal-001",
        source_dataset="Repository MRI smoke fixture",
        source_attribution=(
            "Derived from the tiny synthetic MRI smoke fixture committed in this repository "
            "for honest-ready heuristic MRI demo import validation."
        ),
        original_source=original_source,
        enhanced_source=None,
        liver_mask_source=liver_mask_source,
        liver_mesh_source=liver_mesh_source,
        lesion_mask_path=write_zero_mask(
            source_path=derived_lesion_mask,
            output_path=DEMO_STORAGE_ROOT / normal_slug / "lesion_mask.nii.gz",
        ),
        lesion_mesh_path=None,
        findings=[],
    )

    largest_component = component_stats[0]
    single_mask_path = write_component_mask(
        source_path=derived_lesion_mask,
        output_path=single_root / "lesion_mask.nii.gz",
        selected_indices=set(largest_component.voxel_indices),
    )
    derived_lesion_mask.unlink(missing_ok=True)
    single_mesh_path = write_bbox_cube_glb(
        output_path=DEMO_STORAGE_ROOT / single_slug / "lesion.glb",
        bbox_min=largest_component.bbox_min,
        bbox_max=largest_component.bbox_max,
    )
    build_demo_case(
        case_slug=single_slug,
        modality="MRI",
        category="SINGLE_LESION",
        patient_pseudo_id="demo-mri-single-001",
        source_dataset="Repository MRI smoke fixture",
        source_attribution=(
            "Derived from the tiny synthetic MRI smoke fixture committed in this repository; "
            "the suspicious-zone mask and mesh are deterministically carved from the committed heuristic MRI mask."
        ),
        original_source=original_source,
        enhanced_source=None,
        liver_mask_source=liver_mask_source,
        liver_mesh_source=liver_mesh_source,
        lesion_mask_path=single_mask_path,
        lesion_mesh_path=single_mesh_path,
        findings=[
            finding_from_component(
                largest_component,
                label_prefix="Heuristic suspicious-zone component",
                segment="suspicious-zone",
                suspicion="heuristic-supported",
            )
        ],
    )

def derive_heuristic_suspicious_zone_mask(*, input_path: Path, liver_mask_path: Path, output_path: Path) -> Path:
    input_volume = read_nifti_gz(input_path)
    liver_mask = read_nifti_gz(liver_mask_path)
    voxels = array.array(
        input_volume.typecode,
        [
            1 if mask > 0 and float(intensity) >= 0.45 else 0
            for intensity, mask in zip(input_volume.voxels, liver_mask.voxels)
        ],
    )
    derived_mask = NiftiVolume(
        input_volume.header,
        input_volume.payload_prefix,
        voxels,
        input_volume.shape,
        input_volume.spacing,
        input_volume.datatype,
        input_volume.typecode,
    )
    return write_nifti_gz(output_path, derived_mask, voxels)


if __name__ == "__main__":
    main()
