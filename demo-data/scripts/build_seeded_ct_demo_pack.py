from __future__ import annotations

import array
import gzip
import hashlib
import json
import math
import shutil
import struct
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SOURCE_CASE_ROOT = ROOT / "storage" / "cases" / "1"
DEMO_STORAGE_ROOT = ROOT / "storage" / "demo" / "cases"
MANIFEST_ROOT = ROOT / "demo-data" / "manifests"


@dataclass(frozen=True)
class NiftiVolume:
    header: bytes
    payload_prefix: bytes
    voxels: array.array
    shape: tuple[int, int, int]
    spacing: tuple[float, float, float]
    datatype: int
    typecode: str


@dataclass(frozen=True)
class ComponentStat:
    component_id: int
    voxel_indices: tuple[int, ...]
    bbox_min: tuple[int, int, int]
    bbox_max: tuple[int, int, int]
    centroid: tuple[float, float, float]
    extent: tuple[int, int, int]
    size_mm: float
    volume_mm3: float


def main() -> None:
    MANIFEST_ROOT.mkdir(parents=True, exist_ok=True)
    DEMO_STORAGE_ROOT.mkdir(parents=True, exist_ok=True)

    original_source = SOURCE_CASE_ROOT / "example4d.nii.gz"
    enhanced_source = SOURCE_CASE_ROOT / "example4d.nii.gz.3d.nii.gz"
    liver_mask_source = SOURCE_CASE_ROOT / "example4d.nii.gz.3d.nii.gz.liver_mask.nii.gz"
    lesion_mask_source = SOURCE_CASE_ROOT / "example4d.nii.gz.3d.nii.gz.lesion_mask.nii.gz"
    liver_mesh_source = SOURCE_CASE_ROOT / "example4d.nii.gz.3d.nii.gz.liver.glb"
    lesion_mesh_source = SOURCE_CASE_ROOT / "example4d.nii.gz.3d.nii.gz.lesion.glb"

    lesion_volume = read_nifti_gz(lesion_mask_source)
    component_stats = sorted(
        connected_components(lesion_volume),
        key=lambda component: (-len(component.voxel_indices), component.component_id),
    )
    if not component_stats:
        raise RuntimeError("Expected at least one lesion component in the source multifocal mask")

    normal_slug = "ct-normal-001"
    single_slug = "ct-single-lesion-001"
    multifocal_slug = "ct-multifocal-001"

    build_demo_case(
        case_slug=normal_slug,
        category="NORMAL",
        patient_pseudo_id="demo-ct-normal-001",
        source_dataset="Repository CT smoke fixture",
        source_attribution=(
            "Derived from the tiny synthetic CT smoke fixture committed in this repository "
            "for integration testing and seeded demo import validation."
        ),
        original_source=original_source,
        enhanced_source=enhanced_source,
        liver_mask_source=liver_mask_source,
        liver_mesh_source=liver_mesh_source,
        lesion_mask_path=write_zero_mask(
            source_path=lesion_mask_source,
            output_path=DEMO_STORAGE_ROOT / normal_slug / "lesion_mask.nii.gz",
        ),
        lesion_mesh_path=None,
        findings=[],
    )

    largest_component = component_stats[0]
    single_mask_path = write_component_mask(
        source_path=lesion_mask_source,
        output_path=DEMO_STORAGE_ROOT / single_slug / "lesion_mask.nii.gz",
        selected_indices=set(largest_component.voxel_indices),
    )
    single_mesh_path = write_bbox_cube_glb(
        output_path=DEMO_STORAGE_ROOT / single_slug / "lesion.glb",
        bbox_min=largest_component.bbox_min,
        bbox_max=largest_component.bbox_max,
    )
    build_demo_case(
        case_slug=single_slug,
        category="SINGLE_LESION",
        patient_pseudo_id="demo-ct-single-001",
        source_dataset="Repository CT smoke fixture",
        source_attribution=(
            "Derived from the tiny synthetic CT smoke fixture committed in this repository; "
            "single-lesion mask and mesh are deterministically carved from the largest connected component."
        ),
        original_source=original_source,
        enhanced_source=enhanced_source,
        liver_mask_source=liver_mask_source,
        liver_mesh_source=liver_mesh_source,
        lesion_mask_path=single_mask_path,
        lesion_mesh_path=single_mesh_path,
        findings=[finding_from_component(largest_component)],
    )

    multifocal_root = DEMO_STORAGE_ROOT / multifocal_slug
    multifocal_root.mkdir(parents=True, exist_ok=True)
    multifocal_lesion_mask = multifocal_root / "lesion_mask.nii.gz"
    multifocal_lesion_mesh = multifocal_root / "lesion.glb"
    shutil.copyfile(lesion_mask_source, multifocal_lesion_mask)
    shutil.copyfile(lesion_mesh_source, multifocal_lesion_mesh)
    build_demo_case(
        case_slug=multifocal_slug,
        category="MULTIFOCAL",
        patient_pseudo_id="demo-ct-multifocal-001",
        source_dataset="Repository CT smoke fixture",
        source_attribution=(
            "Derived from the tiny synthetic CT smoke fixture committed in this repository; "
            "multifocal findings come from connected components in the committed lesion mask."
        ),
        original_source=original_source,
        enhanced_source=enhanced_source,
        liver_mask_source=liver_mask_source,
        liver_mesh_source=liver_mesh_source,
        lesion_mask_path=multifocal_lesion_mask,
        lesion_mesh_path=multifocal_lesion_mesh,
        findings=[finding_from_component(component) for component in component_stats],
    )


def build_demo_case(
    *,
    case_slug: str,
    category: str,
    patient_pseudo_id: str,
    source_dataset: str,
    source_attribution: str,
    original_source: Path,
    enhanced_source: Path,
    liver_mask_source: Path,
    liver_mesh_source: Path,
    lesion_mask_path: Path,
    lesion_mesh_path: Path | None,
    findings: list[dict[str, object]],
) -> None:
    case_root = DEMO_STORAGE_ROOT / case_slug
    case_root.mkdir(parents=True, exist_ok=True)

    original_path = case_root / "input.nii.gz"
    enhanced_path = case_root / "enhanced.nii.gz"
    liver_mask_path = case_root / "liver_mask.nii.gz"
    liver_mesh_path = case_root / "liver.glb"

    shutil.copyfile(original_source, original_path)
    shutil.copyfile(enhanced_source, enhanced_path)
    shutil.copyfile(liver_mask_source, liver_mask_path)
    shutil.copyfile(liver_mesh_source, liver_mesh_path)

    lesion_count = len(findings)
    sections = build_report_sections(lesion_count)
    manifest = {
        "schemaVersion": "v1",
        "caseSlug": case_slug,
        "origin": "SEEDED_DEMO",
        "modality": "CT",
        "category": category,
        "patientPseudoId": patient_pseudo_id,
        "sourceDataset": source_dataset,
        "sourceAttribution": source_attribution,
        "artifacts": [
            artifact_entry("ORIGINAL_STUDY", original_path),
            artifact_entry("ENHANCED_VOLUME", enhanced_path),
            artifact_entry("LIVER_MASK", liver_mask_path),
            artifact_entry("LESION_MASK", lesion_mask_path),
            artifact_entry("LIVER_MESH", liver_mesh_path),
            *([] if lesion_mesh_path is None else [artifact_entry("LESION_MESH", lesion_mesh_path)]),
        ],
        "findings": findings,
        "reportData": sections,
        "reportText": assemble_report_text(sections),
    }

    manifest_path = MANIFEST_ROOT / f"{case_slug}.json"
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")


def artifact_entry(artifact_type: str, path: Path) -> dict[str, object]:
    return {
        "type": artifact_type,
        "objectKey": path.relative_to(ROOT / "storage").as_posix(),
        "fileName": path.name,
        "mimeType": mime_type_for(path),
        "sha256": sha256(path),
        "sizeBytes": path.stat().st_size,
    }


def mime_type_for(path: Path) -> str:
    if path.suffix == ".glb":
        return "model/gltf-binary"
    if path.name.endswith(".nii.gz"):
        return "application/gzip"
    return "application/octet-stream"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(8192), b""):
            digest.update(chunk)
    return digest.hexdigest()


def build_report_sections(lesion_count: int) -> dict[str, str]:
    findings = (
        f"Structured output contains {lesion_count} lesion component(s) derived from the lesion mask."
        if lesion_count > 0
        else "Structured output contains no lesion components derived from the lesion mask."
    )
    impression = (
        f"{lesion_count} lesion component(s) were derived from seeded artifact masks and require clinical correlation."
        if lesion_count > 0
        else "No lesion components were derived from the seeded artifact masks."
    )
    limitations = (
        "Seeded demo import reuses artifact-backed findings and report sections; it does not represent a live ML execution. "
        "All outputs remain decision-support only and depend on artifact quality."
    )
    recommendation = "Correlate with source images and radiologist review before clinical use."
    return {
        "findings": findings,
        "impression": impression,
        "limitations": limitations,
        "recommendation": recommendation,
    }


def assemble_report_text(sections: dict[str, str]) -> str:
    return "\n\n".join(
        [
            f"Findings: {sections['findings']}",
            f"Impression: {sections['impression']}",
            f"Limitations: {sections['limitations']}",
            f"Recommendation: {sections['recommendation']}",
        ]
    )


def finding_from_component(component: ComponentStat) -> dict[str, object]:
    return {
        "type": "LESION",
        "label": f"Lesion component #{component.component_id}",
        "confidence": None,
        "sizeMm": component.size_mm,
        "volumeMm3": component.volume_mm3,
        "location": {
            "centroid": [round(value, 3) for value in component.centroid],
            "bbox": {
                "min": list(component.bbox_min),
                "max": list(component.bbox_max),
            },
            "extent": list(component.extent),
        },
    }


def read_nifti_gz(path: Path) -> NiftiVolume:
    with gzip.open(path, "rb") as handle:
        raw = handle.read()
    header = raw[:348]
    dims = struct.unpack("<8h", header[40:56])
    datatype = struct.unpack("<h", header[70:72])[0]
    typecode = {2: "B", 4: "h", 8: "i", 16: "f", 64: "d", 512: "H", 768: "I"}[datatype]
    vox_offset = int(struct.unpack("<f", header[108:112])[0])
    pixdim = struct.unpack("<8f", header[76:108])
    shape = tuple(int(value) for value in dims[1:4])
    voxels = array.array(typecode)
    voxels.frombytes(raw[vox_offset:])
    return NiftiVolume(
        header=header,
        payload_prefix=raw[348:vox_offset],
        voxels=voxels,
        shape=shape,
        spacing=(float(pixdim[1]), float(pixdim[2]), float(pixdim[3])),
        datatype=datatype,
        typecode=typecode,
    )


def write_nifti_gz(path: Path, volume: NiftiVolume, voxels: array.array) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(path, "wb") as handle:
        handle.write(volume.header)
        handle.write(volume.payload_prefix)
        handle.write(voxels.tobytes())
    return path


def write_zero_mask(*, source_path: Path, output_path: Path) -> Path:
    volume = read_nifti_gz(source_path)
    zeroed = array.array(volume.typecode, [0] * len(volume.voxels))
    return write_nifti_gz(output_path, volume, zeroed)


def write_component_mask(*, source_path: Path, output_path: Path, selected_indices: set[int]) -> Path:
    volume = read_nifti_gz(source_path)
    voxels = array.array(volume.typecode, [0] * len(volume.voxels))
    for index in selected_indices:
        voxels[index] = 1
    return write_nifti_gz(output_path, volume, voxels)


def connected_components(volume: NiftiVolume) -> list[ComponentStat]:
    sx, sy, sz = volume.shape
    visited = bytearray(sx * sy * sz)

    def flat_index(x: int, y: int, z: int) -> int:
        return x + sx * (y + sy * z)

    components: list[ComponentStat] = []
    component_id = 1

    for z in range(sz):
        for y in range(sy):
            for x in range(sx):
                start = flat_index(x, y, z)
                if visited[start]:
                    continue
                visited[start] = 1
                if volume.voxels[start] <= 0:
                    continue

                stack = [(x, y, z)]
                voxel_indices: list[int] = []
                sum_x = 0.0
                sum_y = 0.0
                sum_z = 0.0
                min_x = max_x = x
                min_y = max_y = y
                min_z = max_z = z

                while stack:
                    cx, cy, cz = stack.pop()
                    current = flat_index(cx, cy, cz)
                    voxel_indices.append(current)
                    sum_x += cx
                    sum_y += cy
                    sum_z += cz
                    min_x = min(min_x, cx)
                    max_x = max(max_x, cx)
                    min_y = min(min_y, cy)
                    max_y = max(max_y, cy)
                    min_z = min(min_z, cz)
                    max_z = max(max_z, cz)

                    for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                        nx, ny, nz = cx + dx, cy + dy, cz + dz
                        if 0 <= nx < sx and 0 <= ny < sy and 0 <= nz < sz:
                            neighbor = flat_index(nx, ny, nz)
                            if visited[neighbor]:
                                continue
                            visited[neighbor] = 1
                            if volume.voxels[neighbor] > 0:
                                stack.append((nx, ny, nz))

                count = len(voxel_indices)
                extent = (max_x - min_x + 1, max_y - min_y + 1, max_z - min_z + 1)
                size_mm = round(
                    max(extent[0] * volume.spacing[0], extent[1] * volume.spacing[1], extent[2] * volume.spacing[2]),
                    2,
                )
                volume_mm3 = round(count * volume.spacing[0] * volume.spacing[1] * volume.spacing[2], 2)
                components.append(
                    ComponentStat(
                        component_id=component_id,
                        voxel_indices=tuple(voxel_indices),
                        bbox_min=(min_x, min_y, min_z),
                        bbox_max=(max_x, max_y, max_z),
                        centroid=(sum_x / count, sum_y / count, sum_z / count),
                        extent=extent,
                        size_mm=size_mm,
                        volume_mm3=volume_mm3,
                    )
                )
                component_id += 1

    return components


def write_bbox_cube_glb(*, output_path: Path, bbox_min: tuple[int, int, int], bbox_max: tuple[int, int, int]) -> Path:
    output_path.parent.mkdir(parents=True, exist_ok=True)

    x0, y0, z0 = (float(value) for value in bbox_min)
    x1, y1, z1 = (float(value + 1) for value in bbox_max)
    positions = [
        (x0, y0, z0),
        (x1, y0, z0),
        (x1, y1, z0),
        (x0, y1, z0),
        (x0, y0, z1),
        (x1, y0, z1),
        (x1, y1, z1),
        (x0, y1, z1),
    ]
    indices = [
        0, 1, 2, 0, 2, 3,
        4, 6, 5, 4, 7, 6,
        0, 4, 5, 0, 5, 1,
        1, 5, 6, 1, 6, 2,
        2, 6, 7, 2, 7, 3,
        3, 7, 4, 3, 4, 0,
    ]

    positions_blob = b"".join(struct.pack("<3f", *position) for position in positions)
    indices_blob = b"".join(struct.pack("<H", index) for index in indices)
    positions_padded = positions_blob + b"\x00" * ((4 - len(positions_blob) % 4) % 4)
    binary_blob = positions_padded + indices_blob
    binary_blob += b"\x00" * ((4 - len(binary_blob) % 4) % 4)

    gltf = {
        "asset": {"version": "2.0"},
        "scene": 0,
        "scenes": [{"nodes": [0]}],
        "nodes": [{"mesh": 0}],
        "meshes": [{"primitives": [{"attributes": {"POSITION": 0}, "indices": 1, "mode": 4}]}],
        "buffers": [{"byteLength": len(binary_blob)}],
        "bufferViews": [
            {"buffer": 0, "byteOffset": 0, "byteLength": len(positions_blob), "target": 34962},
            {"buffer": 0, "byteOffset": len(positions_padded), "byteLength": len(indices_blob), "target": 34963},
        ],
        "accessors": [
            {
                "bufferView": 0,
                "componentType": 5126,
                "count": len(positions),
                "type": "VEC3",
                "min": [x0, y0, z0],
                "max": [x1, y1, z1],
            },
            {
                "bufferView": 1,
                "componentType": 5123,
                "count": len(indices),
                "type": "SCALAR",
            },
        ],
    }
    json_blob = json.dumps(gltf, separators=(",", ":")).encode("utf-8")
    json_blob += b" " * ((4 - len(json_blob) % 4) % 4)

    total_length = 12 + 8 + len(json_blob) + 8 + len(binary_blob)
    with output_path.open("wb") as handle:
        handle.write(struct.pack("<4sII", b"glTF", 2, total_length))
        handle.write(struct.pack("<I4s", len(json_blob), b"JSON"))
        handle.write(json_blob)
        handle.write(struct.pack("<I4s", len(binary_blob), b"BIN\x00"))
        handle.write(binary_blob)
    return output_path


if __name__ == "__main__":
    main()
