# ML + Viewer E2E Verification

## Verification baseline
Validated by code inspection plus local checks:
- Backend tests: pass
- ML tests: pass
- Frontend production build: pass

## ML pipeline contract
Backend sends ML request with:
- `caseId`
- `modality`
- `executionMode`
- `fileReferences.inputObjectKey`

ML response contract consumed by backend:
- status/modelVersion/metricsJson/report*
- findings[]
- artifact object keys (enhanced, masks, meshes)

Backend persistence behavior:
- Run status transitions: `STARTED -> COMPLETED|FAILED`
- Case lifecycle transitions: `PROCESSING -> COMPLETED|FAILED`
- Audit events: `INFERENCE_STARTED`, `INFERENCE_REQUEST_SENT`, `INFERENCE_COMPLETED` or `INFERENCE_FAILED`

## Tool/model reality matrix
| Tool | Installed in repo | Callable in runtime path | Weights bundled | CT | MRI | Verdict |
|---|---|---|---|---|---|---|
| TotalSegmentator | Adapter + config hooks present | Yes when binary available | No (external) | Primary | Partial/experimental | Partial integration |
| nnUNet v2 | Adapter + config hooks present | Yes when environment configured | No (external) | Lesion stage if configured | MRI lesion uncertain | Partial integration |
| MedSAM | Adapter placeholder present | Not fully wired for mandatory flow | No | Optional | Optional | Stub/experimental |

## 2D viewer verification
Current truthful path:
- Artifact-backed NIfTI loading
- Authenticated backend download URL usage
- Slice navigation + window/level controls
- Overlay support for available mask artifacts

Not implemented:
- DICOM-native OHIF flow (must be labeled missing)

## 3D viewer verification
Current truthful path:
- Artifact-backed liver/lesion mesh loading (GLB/GLTF)
- Visibility + opacity + reset controls
- Degraded message when lesion mesh missing
- No synthetic fake fallback anatomy

## Degraded-state guarantees
When artifacts are missing:
- UI shows unavailable state and guidance
- No fake lesion/liver certainty claims
- No hidden auto-generated geometry

## Smoke scenarios status
1. Auth + empty system: **code path implemented**, runtime full-stack blocked here due no Docker CLI.
2. Create + upload + process: **backend orchestration implemented**, full runtime requires compose environment.
3. Missing artifacts: **implemented graceful UI states**.
4. Real vs mock truth: **improved** by explicit execution mode/model version in status payload.
5. MRI experimental messaging: **present** in case details and docs; lesion path marked partial.
