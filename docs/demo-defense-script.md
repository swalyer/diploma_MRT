# Demo / Defense Script

## Goal
Show the product truthfully in 5-7 minutes:
- seeded CT demo proves the full read path without hidden ML reprocessing
- seeded MRI demo is explicitly honest-ready and heuristic-supported
- live pipeline path exists separately and remains clearly labeled

## Pre-demo checklist
1. Start the stack with `APP_JWT_SECRET=... APP_ML_MODE=mock ML_MODE=mock docker compose up -d --build reverse-proxy frontend backend ml-service postgres-primary audit-postgres minio`.
2. Verify backend health at `http://localhost/actuator/health`.
3. Log in once as `admin@demo.local / Admin123!`.
4. Keep these manifests ready:
   - `demo-data/manifests/ct-single-lesion-001.json`
   - `demo-data/manifests/mri-single-lesion-001.json`
   - `demo-data/manifests/mri-normal-001.json`

## Flow 1: Seeded CT end-to-end
1. Open `Admin`.
2. Import `ct-single-lesion-001.json` if it is not already listed in Ready demo studies.
3. Open the case and point out:
   - `Seeded demo` badge and demo category
   - `Result: seeded import`
   - disabled `Run / rerun pipeline` button
4. Open `2D Imaging`:
   - show NIfTI artifact-backed rendering
   - use `Focus slice` on the lesion finding
5. Open `3D Viewer`:
   - inspect suspicious-zone metadata
   - mention that geometry comes from stored mesh artifacts, not fabricated frontend shapes
6. Open `Artifacts / Technical`:
   - download `ORIGINAL_STUDY`
   - note provenance and artifact-backed read path

## Flow 2: MRI honest-ready
1. Import or open `mri-single-lesion-001`.
2. Point out the explicit `MRI honest-ready · heuristic-supported` label.
3. Open `Report`:
   - show heuristic-safe wording in findings/impression/limitations
4. Open `3D Viewer`:
   - inspect the selected suspicious zone
   - point out `Support: heuristic-supported`
5. Open `mri-normal-001` and show the honest empty-state message when no suspicious-zone mesh exists.

## Flow 3: Live pipeline separation
1. Create a new live case from `Create Case`.
2. Upload a NIfTI study.
3. Show that live cases keep `Run / rerun pipeline` enabled and use backend execution mode/status instead of seeded import semantics.
4. State clearly that real CT depends on external model tooling/weights, while MRI remains heuristic-supported in the current scope.

## Key defense lines
- Seeded and live cases are distinct in the domain model via `CaseOrigin`.
- Seeded imports reuse the normal case read path instead of a second viewer/report architecture.
- Reports are deterministic and evidence-bound to structured findings and manifest metadata.
- Referenced demo artifacts are not treated like managed binaries during cleanup.
- The frontend contract now derives enum truth from backend source, reducing drift across layers.

## If asked about limitations
- OHIF / DICOM-native workflow is still missing; the implemented 2D path is NIfTI-only.
- Full real CT quality depends on external model binaries and weights not bundled in git.
- MRI lesion/suspicious-zone output is intentionally presented as heuristic-supported, not fully model-backed.
