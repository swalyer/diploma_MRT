# Frontend implementation audit (Phase 0)

Date: 2026-03-02
Scope: `frontend/` current codebase and relevant backend API contracts consumed by frontend.

## A) Auth and route protection

## Implemented
- Protected routes exist in `App.tsx` via `ProtectedRoute` wrapper around `/cases`, `/cases/new`, `/cases/:id`, `/admin`.
- Axios request interceptor injects `Authorization: Bearer <token>` for API calls.
- Token is persisted in `localStorage` (`mrt.auth.token`) and restored on refresh.
- 401 responses clear auth state and trigger redirect to `/login` using an `auth:expired` event.

## Partial
- There is no refresh-token workflow. Expired token = forced logout.
- No idle timeout warning UI.

## Missing
- No role-aware route guard in frontend (all authenticated users can access `/admin` UI route).

## Misleading / overclaimed risk
- Frontend route protection is real, but backend remains source of truth for authorization; frontend alone is not a security boundary.

## B) 2D viewer reality check

## Implemented
- Viewer is artifact-backed and fetches binaries from backend artifact download URL.
- Request includes bearer token for artifact fetch.
- NIfTI parsing is implemented with `nifti-reader-js`.
- Real controls implemented: slice slider, window/level sliders, liver overlay, lesion overlay.
- Source volume selector implemented (`ENHANCED_VOLUME`, `NORMALIZED_VOLUME`, `ORIGINAL_STUDY`).

## Partial
- Viewer is canvas-based and NIfTI-only in current frontend implementation.
- Before/after is available as source selection, not a synchronized dual-pane compare view.

## Placeholder
- OHIF/DICOM-native workflow is not integrated yet (explicitly labeled pending in UI copy).

## Missing
- DICOM series native rendering and OHIF toolchain (measurements/annotations presets) are missing.

## C) 3D viewer reality check

## Implemented
- 3D scene renders mesh artifacts loaded from backend artifact download endpoint using `useGLTF`.
- Liver and lesion visibility toggles are real.
- Liver opacity slider is real.
- Reset camera is real (canvas remount key).
- Screenshot export is real (canvas `toDataURL`).
- Missing lesion mesh state is explicitly shown.

## Partial
- Only GLB/GLTF loader path is implemented in frontend.
- Lesion click interaction currently returns coarse selection message; lesion metadata endpoint wiring is not implemented.

## Missing
- OBJ/STL/VTK loader support in frontend viewer.
- Rich lesion metadata side panel populated from dedicated mesh metadata API.

## D) ML/model reality check from frontend perspective

## Implemented
- Frontend surfaces capability states derived from returned artifacts (liver mask, lesion mask, liver mesh, lesion mesh availability).
- Pipeline timeline surfaced from `status.stageAuditTrail`.

## Partial
- Execution mode shown as inferred (`real-or-hybrid` vs `mock-or-pending`) from artifacts, because explicit `executionMode` is not currently exposed by backend status DTO.
- MRI experimental messaging is shown as fixed caution label (frontend cannot prove modality-specific model health from current payloads).

## Missing
- No model version/stage timings in currently consumed status/report endpoints.
- No explicit frontend field for "weights missing" reason beyond missing artifacts.

## Misleading / overclaimed risk
- Any strict claim of "full real mode detected" would be overclaim with current API; UI now labels this as inferred/partial.

## E) Product UX reality check

## Implemented improvements
- Rich login split layout with product context and safety disclaimer.
- Structured app shell with identity, navigation, breadcrumbs, session badge.
- Cases dashboard with filters, skeleton loading, status chips.
- Intake flow with drag/drop zone, format validation, staged guidance.
- Flagship case details with capability chips, overview/report/imaging/3D/technical/audit tabs.

## Partial
- Notification/toast framework is not yet centralized.
- Case list lacks creator/suspicious lesion because those fields are not currently provided by list API.

## Missing
- Full role-based UI partitioning for admin sections.
- OHIF-grade imaging workstation interactions.

## Overall classification summary
- **Implemented**: auth guards + tokenized API, NIfTI artifact-backed 2D viewer, artifact-backed GLB/GLTF 3D viewer, premium layout baseline.
- **Partial**: execution-mode truth surfacing (inferred), lesion interaction depth, admin operability.
- **Placeholder**: OHIF mention only; no hidden fake implementation.
- **Missing**: DICOM-native viewer, advanced mesh formats, model version/timing fields in frontend payloads.
- **Misleading resolved**: UI copy now explicitly labels pending/experimental/unavailable states instead of implying completion.
