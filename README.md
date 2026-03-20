# Liver MRI/CT Analysis MVP

Incremental production-like MVP for liver CT/MRI decision support. The project keeps module boundaries:
- `backend/`: Spring Boot API + auth + orchestration
- `frontend/`: React UI with timeline/tabs/3D controls
- `ml-service/`: FastAPI inference with `mock` and `real` execution modes
- `database/`: migrations and example API payloads
- `storage/`: local artifact storage (object-key based)

> ⚠️ Clinical safety: outputs are **decision-support only** and must be verified by a physician.

## Supported modalities
- **CT**: primary real pipeline for uploaded NIfTI studies (`.nii`, `.nii.gz`) with liver segmentation and lesion segmentation when weights exist.
- **MRI**: heuristic-supported. Liver and suspicious-zone paths are available in real mode; dedicated MRI weights still improve quality when configured.

## Datasets referenced for benchmarking/dev scripts
- 3D-IRCADb-01 (CT end-to-end smoke tests)
- MSD Task03 Liver (CT liver/tumor)
- CHAOS (MRI/CT organ segmentation)
- LiverHccSeg (MRI liver/HCC)
- OpenSwissHCC (MRI HCC validation)
- Optional: CT-ORG, HCC-TACE-SEG, TCGA-LIHC

## Run locally (Docker, segmented MVP topology)
```bash
docker compose up --build -d
```

Services:
- Ingress + Frontend: http://localhost
- Backend API (via ingress): http://localhost/api
- Backend Swagger (internal direct): http://localhost:8080/swagger-ui/index.html
- MinIO console (admin): http://localhost:9001

The local compose keeps explicit logical segments (`edge_net`, `app_net`, `data_net`, `replication_net`, `obs_net`) so the same network model can be defended in a multi-host setup.

## Deterministic startup and demo login
1. Start full stack:
   ```bash
   docker compose up --build -d
   ```
2. Verify health:
   ```bash
docker compose ps

## CI

GitHub Actions workflow lives in [`.github/workflows/ci.yml`](./.github/workflows/ci.yml) and runs:

- `backend`: `mvn test -q`
- `frontend`: `npm ci && npm run build`
- `ml-service`: `docker compose build ml-service-test` and `docker compose run --rm --no-deps ml-service-test pytest -q`
- `browser-e2e`: starts the mock stack via Docker Compose and runs Playwright from [`frontend/e2e`](./frontend/e2e)
   curl -fsS http://localhost/actuator/health
   ```
3. Demo credentials (both seeded by Flyway after schema migration):
   - `admin@demo.local` / `Admin123!`
   - `doctor@demo.local` / `Admin123!`
   - Local Docker Compose enables these demo users explicitly via `APP_DEMO_USERS_ENABLED=true`; keep this disabled outside local/demo environments.
4. Login smoke test:
   ```bash
   curl -i -X POST http://localhost/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{"email":"admin@demo.local","password":"Admin123!"}'
   ```
5. Create case smoke test (replace `<JWT>` with token from login):
   ```bash
   curl -i -X POST http://localhost/api/cases \
     -H "Authorization: Bearer <JWT>" \
     -H "Content-Type: application/json" \
     -d '{"patientPseudoId":"demo-patient-1","modality":"CT"}'
   ```

### Troubleshooting
- `no main manifest attribute, in app.jar`: backend image built the wrong artifact; rebuild with `docker compose build backend --no-cache`.
- `unzip: not found` during backend image build: use the latest repository version where manifest validation uses JDK `jar` tooling (no `unzip` dependency), then rebuild without cache:
  ```bash
  git pull
  docker compose build backend --no-cache
  ```
- macOS note: `apt-get` is expected **inside Debian-based containers**, not on your Mac host shell. Do not run `apt-get` locally on macOS.
- `relation "app_user" does not exist` during Postgres init, or `Found non-empty schema(s) "public" but no schema history table`: reset volumes so Flyway can recreate schema+seed deterministically on the primary DB:
  ```bash
  docker compose down -v
  docker compose up --build -d
  ```
- Backend health not ready yet: check `docker compose logs backend -f` until `/actuator/health` returns `{"status":"UP"}`.
- `Schema-validation: missing table [analysis_case]`: schema was initialized from an older naming revision (`case_entity`/`case`) and now conflicts with JPA mapping. The repository includes a reconciliation migration; for a guaranteed clean local re-init run:
  ```bash
  docker compose down -v
  docker compose up --build -d
  ```
- Verify which DB Hibernate/JPA validates against (Hikari pool URL):
  ```bash
  LOGGING_LEVEL_COM_ZAXXER_HIKARI=DEBUG docker compose up -d --force-recreate backend
  docker compose logs backend --tail=200 | rg "jdbcUrl="
  ```
  The `jdbcUrl=...` line for the primary pool must point to `postgres-primary`/`mrt`.
- Schema mismatch (`analysis_case` missing) even when Flyway ran: ensure Hibernate default schema is `public` (or unset) and not overridden by `hibernate.default_schema` / `spring.jpa.properties.hibernate.default_schema` / `@Table(schema=...)`.
- Multi-datasource wiring guardrails: primary JPA must use `spring.datasource` (`@Primary` `dataSource` bean), while `audit` and `read replica` datasources stay conditional (`app.audit.enabled`, `app.read-replica-enabled`) and non-primary so they cannot become default for JPA.

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
1. Upload CT/MRI case artifact in NIfTI format (`.nii` or `.nii.gz`) and store its object key server-side.
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
- Upload API currently accepts only NIfTI (`.nii`, `.nii.gz`). DICOM/ZIP ingestion is not exposed in the MVP upload contract until a converter-backed pipeline is added end-to-end.
- MRI lesion path has heuristic fallback; dedicated MRI weights are still recommended for clinical-grade quality.
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
   - upload `.nii/.nii.gz`
   - run pipeline
   - inspect timeline, report, artifacts, 2D/3D tabs

## Seeded CT Demo Pack
- Committed seeded CT manifests live in [`demo-data/manifests`](./demo-data/manifests):
  - `ct-normal-001.json`
  - `ct-single-lesion-001.json`
  - `ct-multifocal-001.json`
- Lightweight artifact-backed demo files live under [`storage/demo/cases`](./storage/demo/cases).
- Regenerate the committed demo pack from the repository smoke fixture with:
  ```bash
  python3 demo-data/scripts/build_seeded_ct_demo_pack.py
  ```
- Run backend/API seeded smoke against a live local stack with:
  ```bash
  python3 demo-data/scripts/smoke_seeded_case_api.py
  ```
- Admin import flow:
  - login as `admin@demo.local`
  - open `/admin`
  - paste a manifest JSON from `demo-data/manifests`
  - import and open the seeded case

## What is real vs mock vs experimental (strict)
- **Real (when configured):** CT segmentation pipeline stages backed by external model tooling.
- **Mock:** deterministic artifact/result generation with `ML_MODE=mock`.
- **Heuristic-supported:** MRI path and no-weights lesion fallback branches.
- **Missing:** OHIF DICOM-native frontend flow.

## Distributed deployment docs
- `docs/network-architecture.md`
- `docs/deployment-topology.md`
- `docs/database-topology.md`
- `docs/ports-and-flows.md`
- diagrams in `docs/diagrams/*.mmd`

Host-oriented compose files:
- `docker-compose.edge.yml`
- `docker-compose.app.yml`
- `docker-compose.data.yml`
- `docker-compose.observability.yml`

Environment contract for distributed setup is documented in `.env.example` (including `APP_PUBLIC_BASE_URL`, `VITE_API_BASE_URL`, `APP_DB_PRIMARY_*`, `APP_DB_REPLICA_*`, `APP_AUDIT_DB_*`, `APP_STORAGE_*`, `APP_OBSERVABILITY_ENABLED`, `APP_LOG_LEVEL`).
