# Frontend audit (implementation truth + state correctness)

Date: 2026-03-02
Scope: `frontend/` behavior with references to consumed backend DTOs/endpoints.

## A. Session/auth behavior

- **Implemented**
  - JWT token persistence across refresh via `localStorage` in `authStore`.
  - Route protection with redirect to `/login` when token missing.
  - Axios request interceptor injects bearer token.
  - 401 handling clears token and dispatches `auth:expired`, app redirects to login.
- **Partial**
  - No refresh token flow.
  - 403 is not specially surfaced (treated as generic request failure in pages).
  - `/admin` route has no frontend role gate; backend remains authorization source.
- **Misleading risk**
  - Frontend guard is UX/security hygiene only; backend authz is authoritative.

## B. Cases page state model

- **Implemented** explicit hierarchy:
  - `loading`
  - `error`
  - `success-empty`
  - `success-results`
- **Verified correction**
  - Error and empty can no longer render simultaneously.
  - Filtered-empty message appears only in successful load state.
- **Partial**
  - Error messaging still aggregates token invalid/backend down/access denied into one user-facing string.

## C. Case details state model

- **Implemented** explicit states:
  - `loading`, `error`, `success`, `success-degraded`.
- **Verified correction**
  - If core case request fails (`/cases/{id}`, `/status`, `/artifacts`, `/viewer/3d`), page renders a minimal safe error shell only.
  - No confident capability chips/tabs data is shown when core fetch failed.
- **Truth model**
  - Execution mode shown as **inferred** from artifact presence.
  - Model version shown as **not exposed by API**.
  - Missing weights shown as **not explicitly surfaced by API**.
- **Partial**
  - Degraded state tracks report/findings partial failures, but without machine-readable backend reason taxonomy.

## D. 2D viewer truth

- **Implemented**
  - Real NIfTI artifact-backed rendering.
  - Authenticated file fetch (`Authorization` header attached).
  - Real slice navigation + window width/center controls.
  - Real liver/lesion overlay toggles from mask artifacts.
- **Partial**
  - NIfTI path only.
  - Single-canvas viewer (not OHIF workstation parity).
- **Placeholder / Missing**
  - OHIF/DICOM-native support is pending and explicitly labeled pending in UI.

## E. 3D viewer truth

- **Implemented**
  - Mesh ID lookup from `/cases/{id}/viewer/3d`.
  - Artifact-backed loading via `/api/files/{artifactId}/download`.
  - Real liver/lesion visibility toggles.
  - Real liver opacity control.
  - Real reset camera.
  - Real screenshot export.
- **Partial**
  - Lesion click metadata remains coarse (selection message only).
  - GLB/GLTF support only in current frontend loader.
- **Missing**
  - Structured lesion metadata endpoint integration.
  - Additional mesh formats in frontend.
- **Misleading risk status**
  - No fake fallback geometry in current component.

## F. Model surfacing truth

- **Implemented**
  - UI labels inferred capability state from artifacts.
  - UI separates verified availability (artifact exists) from unavailable state.
  - MRI marked experimental.
- **Partial**
  - `executionMode` is inferred (not explicit in status DTO used by frontend).
  - model version not available in consumed status payload.
- **Missing**
  - explicit `weights_missing` style machine-readable reason in current UI payloads.

## Final checklist (claim mapping)

- Premium login composition: **Implemented**
- Cases state correctness (no error+empty conflict): **Implemented**
- Case details no false-confidence on failed core fetch: **Implemented**
- Explicit page-state modeling: **Implemented**
- 2D NIfTI viewer: **Implemented**
- DICOM/OHIF native viewer: **Missing**
- 3D artifact-backed liver/lesion rendering: **Implemented (partial for lesion metadata)**
- Execution mode explicit from API: **Missing (inferred only)**
- Model version display from API: **Missing (not exposed)**
- Missing weights reason taxonomy: **Missing**
