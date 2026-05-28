# Viewer and model verification (strict truth report)

Date: 2026-03-02

## 3D verification

- **3D renderer component**: `frontend/src/components/Viewer3D.tsx`.
- **Mesh metadata endpoint**: `GET /api/cases/{id}/viewer/3d` (returns liver/lesion mesh artifact IDs).
- **Mesh file endpoint**: `GET /api/files/{artifactId}/download`.
- **Artifact truth**: viewer renders only backend-provided mesh artifacts; no synthetic fallback anatomy code path exists.

### Supported formats
- **Implemented**: GLB/GLTF (`useGLTF`).
- **Missing**: OBJ/STL/VTK loaders in current frontend.

### Feature truth matrix
- Liver mesh render: **Implemented** (artifact-backed).
- Lesion mesh render: **Implemented when artifact exists**.
- Lesion-missing degradation: **Implemented** (liver viewer still usable).
- Reset camera: **Implemented** (canvas key remount).
- Screenshot export: **Implemented** (`canvas.toDataURL`).
- Lesion click metadata: **Partial** (selection notice only, no structured metadata payload binding).
- Fake geometry fallback: **Missing by design** (none present).

## 2D verification

- **2D renderer component**: `frontend/src/components/Medical2DViewer.tsx`.
- **File access path**: artifact download URLs from backend artifact inventory.
- **Auth handling**: artifact fetch includes bearer token.
- **Implemented controls**: base source selection, slice navigation, window width/center, liver/lesion overlays.
- **Pending support**: OHIF/DICOM-native workflow is not implemented and explicitly labeled pending.

## Model-output surfacing verification

- Mock vs real state in UI: **inferred** from artifact presence (`LIVER_MASK` etc.), not explicit execution mode field.
- Execution mode explicit in status DTO consumed by frontend: **No** (inferred only).
- MRI status: **experimental** banner/chip shown.
- Mesh availability: **explicit** from viewer 3D payload IDs.
- Missing model weights: **not explicitly surfaced** (no dedicated backend reason field consumed by frontend).
- Model version in case status UI: **not exposed by API payload currently used**.

## Integrity conclusions

- Viewer surfaces are implementation-honest for current shipped behavior.
- Major truth gaps are API-contract level (explicit execution mode/model version/reason taxonomy), not hidden by frontend wording.
