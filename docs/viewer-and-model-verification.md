# Viewer and model-backed flow verification

Date: 2026-03-02

## 3D verification

- **Frontend 3D renderer component**: `frontend/src/components/Viewer3D.tsx`.
- **Backend endpoint for mesh ids**: `GET /api/cases/{id}/viewer/3d` (consumed in `CaseDetailsPage`).
- **Backend endpoint for mesh files**: `GET /api/files/{artifactId}/download` (used as GLTF loader URL).
- **Storage-backed reality**: frontend never creates synthetic anatomy fallback; it only renders meshes when artifact IDs exist.

### Accepted mesh formats (current frontend)
- Implemented loader: `useGLTF` => practical support is **GLB/GLTF**.
- Not implemented in frontend: OBJ/STL/VTK loaders.

### Feature truth table
- Liver mesh render: **Implemented (real artifact-backed)**.
- Lesion mesh render: **Implemented when artifact id exists**.
- Lesion missing state: **Implemented with explicit warning**.
- Click lesion metadata panel: **Partial** (selection alert only; no structured metadata endpoint binding).
- Reset camera: **Implemented** (canvas remount).
- Screenshot export: **Implemented** (`canvas.toDataURL` download).
- Fake mesh fallback: **Not present** in current component.

## Model verification from frontend

### Where UI indicates mock vs real
- Case details capability mode is inferred from artifact presence:
  - `real-or-hybrid` if liver mask artifact exists.
  - `mock-or-pending` otherwise.
- This is intentionally labeled as inferred because status DTO currently does not expose explicit `executionMode`.

### Output types frontend expects/displays
- Liver mask artifact (`LIVER_MASK`)
- Lesion mask artifact (`LESION_MASK`)
- Liver mesh artifact id (`viewer/3d` response)
- Lesion mesh artifact id (`viewer/3d` response)
- Report text (`/report`)
- Structured findings (`/findings`)
- Stage audit trail (`/status`)

### Distinguishing partial model paths
- Frontend can distinguish:
  - liver outputs available / unavailable
  - lesion outputs available / unavailable
  - mesh outputs available / unavailable
- Frontend cannot yet deterministically distinguish:
  - "lesion model unconfigured" vs "no lesion found" vs "stage failed" (reason is not provided as dedicated machine-readable field in consumed payloads).

### Honesty check
- UI labels MRI as experimental.
- UI avoids claiming OHIF/DICOM is implemented.
- UI avoids claiming explicit model-version/timing traceability where API does not provide it.

## Recommendation to close remaining truth gaps
1. Extend backend status DTO with `executionMode`, `modelVersion`, `stageTimings`, and machine-readable stage warnings.
2. Add dedicated lesion metadata endpoint for 3D click interactions.
3. Add explicit failure reason taxonomy (`weights_missing`, `no_lesion_detected`, `adapter_error`).
