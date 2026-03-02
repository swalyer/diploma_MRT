# Liver MRI/CT Analysis MVP

Incremental production-like MVP for liver CT/MRI decision support. The project keeps module boundaries:
- `backend/`: Spring Boot API + auth + orchestration
- `frontend/`: React UI with timeline/tabs/3D controls
- `ml-service/`: FastAPI inference with `mock` and `real` execution modes
- `database/`: migrations and example API payloads
- `storage/`: local artifact storage (object-key based)

> ⚠️ Clinical safety: outputs are **decision-support only** and must be verified by a physician.

## Supported modalities
- **CT**: primary real pipeline (DICOM/NIfTI → liver segmentation → lesion segmentation when weights exist).
- **MRI**: experimental. Liver mask path is available; lesion analysis may be unavailable until MRI lesion weights are configured.

## Datasets referenced for benchmarking/dev scripts
- 3D-IRCADb-01 (CT end-to-end smoke tests)
- MSD Task03 Liver (CT liver/tumor)
- CHAOS (MRI/CT organ segmentation)
- LiverHccSeg (MRI liver/HCC)
- OpenSwissHCC (MRI HCC validation)
- Optional: CT-ORG, HCC-TACE-SEG, TCGA-LIHC

## Run locally (Docker)
```bash
docker compose up --build
```

Services:
- Frontend: http://localhost:5173
- Backend Swagger: http://localhost:8080/swagger-ui/index.html
- ML service docs: http://localhost:8000/docs

## ML execution modes
- `ML_MODE=mock` — deterministic mock artifacts.
- `ML_MODE=real` — adapter-driven real path:
  - TotalSegmentator adapter for liver masks.
  - nnUNet v2 adapter for lesion masks (if weights configured).
  - MedSAM adapter placeholder for optional interactive fallback.

### Config examples
- `ml-service/config/models.mock.example.yml`
- `ml-service/config/models.real.example.yml`

### Model and dataset scripts
```bash
./ml-service/scripts/download_models.sh
./ml-service/scripts/prepare_datasets.sh
```

## Inference flow
1. Upload CT/MRI case artifact (object key stored server-side).
2. Backend sends ML request with `caseId`, `modality`, `executionMode`, and `fileReferences`.
3. ML returns artifact keys for enhanced volume, masks, meshes + findings + metrics.
4. Backend persists results and status audit trail (started/request/completed/failed).
5. Frontend displays timeline, artifacts, report, mock/real badge, and 3D controls.

## Security
- JWT required for all business endpoints (`/api/auth/**` is public).
- Ownership checks enforced for case/artifact/report/findings/status endpoints.
- Public DTOs expose artifact IDs + download URLs, not local filesystem paths.

## Tests
- ML mock pipeline test.
- ML CT real pipeline smoke test with tiny synthetic NIfTI fixture.
- Backend process/result flow test for persistence.

## Known limitations
- Real inference depends on locally installed TotalSegmentator/nnUNet and weights.
- MRI lesion path is experimental and may return liver-only results.
- 2D viewer is now artifact-backed for NIfTI (window/level + overlays); full OHIF DICOM-native workflow is still pending.
- 3D viewer loads generated liver/lesion GLB meshes when artifacts exist; if lesion mesh is absent, UI explicitly reports the reason instead of rendering synthetic geometry.


## Frontend capability status (honest snapshot)
- **Implemented**: authenticated app shell, protected routes, case dashboard, intake flow, artifact-backed NIfTI 2D viewer, artifact-backed GLB/GLTF 3D viewer, report/artifact/technical tabs.
- **Partial**: lesion click metadata is coarse, admin controls are informational.
- **Missing**: OHIF DICOM-native viewer, OBJ/STL/VTK frontend mesh loaders.

## Frontend acceptance checklist
- Login redesigned (premium layout): **implemented**
- App shell with route protection and session handling: **implemented**
- Cases list polished with filters/states: **implemented**
- Create/upload flow with drag-drop and validation: **implemented**
- Case details as flagship screen: **implemented**
- 2D viewer (artifact-backed): **implemented** (NIfTI path), DICOM-native: **missing**
- 3D viewer (artifact-backed): **implemented** (GLB/GLTF), advanced metadata interactions: **partial**
- Truthful real/mock/experimental messaging: **implemented**
- Admin operational controls: **partial**


## Frontend MVP claim matrix (implemented vs partial vs missing vs inferred)
- Cases page explicit state hierarchy (loading/error/success-empty/success-results): **implemented**
- Case details core-failure safe shell (no pseudo-known chips on failed fetch): **implemented**
- Execution mode displayed as authoritative backend field: **implemented**
- Execution mode displayed as inferred from artifacts: **partial fallback only when status missing**
- Model version shown from case-status API: **implemented**
- 2D imaging NIfTI artifact-backed viewer: **implemented**
- DICOM/OHIF native viewer workflow: **missing**
- 3D liver mesh viewer (artifact-backed): **implemented**
- 3D lesion metadata interactions: **partial**
- Missing-model-weights explicit reason in UI: **missing**


## Integration verification artifacts
- `docs/system-integration-audit.md`
- `docs/ml-and-viewer-e2e-verification.md`
- `docs/final-capability-matrix.md`

## Demo env quickstart
1. Backend env comes from compose defaults, override as needed:
   - `APP_ML_MODE=mock|real`
   - `APP_JWT_SECRET`
2. ML env:
   - `ML_MODE=mock|real`
   - `ML_MODELS_CONFIG_PATH` to a real model profile when using `real`
3. Start services:
   - `docker compose up --build`
4. Demo flow:
   - register/login
   - create case
   - upload `.nii/.nii.gz/.dcm/.zip`
   - run pipeline
   - inspect timeline, report, artifacts, 2D/3D tabs

## What is real vs mock vs experimental (strict)
- **Real (when configured):** CT segmentation pipeline stages backed by external model tooling.
- **Mock:** deterministic artifact/result generation with `ML_MODE=mock`.
- **Experimental:** MRI path and MedSAM-assisted branches.
- **Missing:** OHIF DICOM-native frontend flow.
