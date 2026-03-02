# System Integration Audit (code + runtime checks)

## Scope and method
Audit performed from repository code paths and local command checks.

Commands executed:
- `cd backend && mvn test -q`
- `cd ml-service && pytest -q`
- `cd frontend && npm run build`
- `docker compose config` (failed: docker CLI absent in environment)

## A. Environment and startup
| Item | Status | Evidence |
|---|---|---|
| Compose file exists and wires postgres/minio/backend/ml/frontend | Implemented | `docker-compose.yml` defines all 5 services with ports/volumes/env. |
| Compose reproducibility in this environment | Partial | `docker compose config` could not run because `docker` CLI is unavailable. |
| Backend required envs | Implemented | `SPRING_DATASOURCE_*`, `APP_JWT_SECRET`, `APP_ML_URL`, `APP_STORAGE_ROOT`, `APP_ML_MODE` are consumed by compose/application. |
| ML required envs | Implemented | `ML_MODE`, `ML_ARTIFACTS_ROOT`, `ML_MODELS_CONFIG_PATH` are provided in compose. |
| Hidden manual steps | Partial | Real mode still depends on external model binaries/weights configured outside repo scripts. |

## B. Backend reality
| Capability | Status | Notes |
|---|---|---|
| Auth endpoints + JWT | Implemented | `AuthController`, `AuthServiceImpl`, `JwtAuthenticationFilter`, `SecurityConfig`. |
| Protected routes | Implemented | `SecurityConfig` protects `/api/**` except `/api/auth/**`. |
| Case CRUD + ownership checks | Implemented | `CaseServiceImpl.findOwnedCase()` used before detail/result operations. |
| Upload/process/status/results | Implemented | CaseController + ResultController + async process in CaseServiceImpl. |
| Artifact download auth | Implemented | `FileController` validates ownership before `StorageService.load`. |
| Local path leakage via DTO | No leakage in DTO, internal storage key persisted | DTO returns `/api/files/{id}/download`; object key stored in DB only. |
| Audit trail persisted | Implemented | `AuditServiceImpl` writes `AuditEvent`; status assembles timeline from events. |
| Execution mode/model version exposed | **Fixed to implemented** | `StatusResponse` now includes `executionMode`, `modelVersion`, `metricsJson`. |

## C. ML-service reality
| Capability | Status | Notes |
|---|---|---|
| Mock pipeline | Implemented | `app/pipeline/mock_pipeline.py` tested by `test_mock_pipeline.py`. |
| Real pipeline orchestration | Partial | `real_pipeline.py` has staged flow; depends on external tooling availability. |
| Adapter wiring (TotalSegmentator / nnUNet / MedSAM) | Partial | Adapters exist; runtime usability depends on installed tools and weights. |
| Missing weights behavior | Implemented/Graceful | Real pipeline adapters gate by config and return partial outputs. |
| MRI lesion path | Experimental/Partial | MRI messaging should remain experimental; lesion path may be absent. |

## D. Frontend reality
| Capability | Status | Notes |
|---|---|---|
| Auth flow + token persistence + 401 handling | Implemented | `authStore.ts` + axios client interceptors + protected app shell. |
| Cases page state consistency | Implemented | Distinct loading/error/empty/success handling in `CasesPage.tsx`. |
| Case details contradictory-state prevention | Implemented | Explicit `error` shell avoids confident chips when core data fails. |
| 2D viewer | Implemented (artifact-backed NIfTI) | `Medical2DViewer.tsx`; OHIF/DICOM-native remains missing. |
| 3D viewer | Implemented (artifact-backed mesh) | `Viewer3D.tsx` uses mesh artifacts; no fake synthetic anatomy fallback. |
| Execution/mode version surfacing | **Fixed to implemented** | UI now renders backend status fields instead of artifact-only inference. |

## E. End-to-end data truth model
- **Backend-origin explicit**: case identity/modality/status, audit trail, artifact metadata + download URLs, findings/report when present.
- **ML-origin explicit**: modelVersion, metricsJson, artifacts object keys, findings/report payload (via infer response).
- **Frontend inferred**: availability chips from artifact presence.
- **Unavailable unless configured**: real model stages requiring external weights/binaries.
- **Mock-only**: deterministic mock mode artifacts and findings.
- **MRI**: experimental and partially supported; must be presented as such.

## Key risks and fixes completed in this pass
1. Backend build instability due Lombok annotation processing under this toolchain → fixed in `pom.xml`.
2. Java 25 + Mockito inline compatibility issue in tests → fixed via surefire `-Dnet.bytebuddy.experimental=true`.
3. Status truth gap (execution mode/model version not surfaced) → fixed in backend DTO/service + frontend rendering.
4. Null artifact object keys could create misleading artifact rows → guarded in backend `addArtifact`.
