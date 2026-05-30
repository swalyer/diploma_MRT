# QA Test Plan — Liver CT/MRI Decision-Support MVP

## Scope
End-to-end verification of the product surfaces a clinician and an admin touch:
authentication, role-based access, case intake, the real (mock-backed) inference
pipeline, structured/PDF reporting, the 2D and 3D viewers, the interactive
findings panel, and the seeded-demo import path. Tests run against the running
stack (frontend → backend → ml-service → PostgreSQL), not against mocks of the
backend, so contract and integration regressions are caught.

> Decision-support only — these tests validate software behaviour, not clinical
> accuracy.

## Test layers
| Layer | Tooling | What it covers |
| --- | --- | --- |
| ML unit/smoke | pytest (`ml-service`) | pipelines, report builder, OpenAPI contract, GPU-gated Dice |
| Backend unit/service | JUnit (`backend`) | domain state, importer, report PDF, audit, SSE publisher |
| Contract | JVM `FrontendContractGenerator --check` | TS enum bundle matches the JVM wire model |
| **E2E (this plan)** | Playwright (`frontend/e2e`) | full user journeys + negative/RBAC across the live stack |

## E2E suites (`frontend/e2e/tests`)
| Spec | Scenarios |
| --- | --- |
| `auth-rbac.spec.ts` | bad-password error, unauthenticated redirect, doctor has no Admin nav, admin opens console, logout re-protects routes |
| `negative-api.spec.ts` | wrong password 4xx, unauthenticated 401/403, missing case 404, doctor→admin 403, non-NIfTI upload 4xx |
| `happy-path.spec.ts` | doctor: intake → upload → process → COMPLETED → report + signable PDF + artifacts + 2D + 3D |
| `findings-interaction.spec.ts` | findings panel rows, row selection, "View in 2D" navigation, labels in report tab |
| `theme.spec.ts` | light/dark toggle flips and persists across reload |
| `case-flow.spec.ts` | original create/upload/process/review smoke |
| `admin-demo-import.spec.ts` | admin imports a seeded manifest and opens the case |
| `seeded-case-viewers.spec.ts` | seeded CT case viewers + artifact download |
| `seeded-mri-case.spec.ts` | MRI honest-ready labelling across report/2D/3D |

## Coverage matrix (requirement → spec)
| Requirement | Verified by |
| --- | --- |
| FR-1/3 modality + lesion segmentation path | happy-path, seeded-mri |
| FR-5 3D visualization | happy-path, seeded-case-viewers |
| FR-6 signable report | happy-path (PDF), report tab specs |
| FR-7 per-finding metadata | findings-interaction |
| FR-8 auth + roles | auth-rbac, negative-api |
| NFR-3 honest mode labelling | seeded-mri, happy-path chips |
| NFR-5 security (authz) | negative-api, auth-rbac |

## How to run
Prerequisites: the stack is up (frontend :5173, backend :8080, ml-service :8000,
PostgreSQL :5432) with demo users seeded (`APP_DEMO_USERS_ENABLED=true`).

```bash
cd frontend/e2e
npm install
npx playwright install chromium
PLAYWRIGHT_BASE_URL=http://localhost:5173 npx playwright test
```

In CI the same specs run against the mock Docker Compose stack behind nginx
(`PLAYWRIGHT_BASE_URL=http://localhost`).

## Exploratory edge-case probe
`frontend/e2e/exploratory-probe.mjs` exercises domain edges the scripted specs
don't: seeded-import idempotency (no duplicate on re-import), delete → 404,
injection-like `patientPseudoId` stored/echoed literally (React escapes on
render), a doctor being blocked (403) from processing a seeded demo case, and
double-process returning a graceful `202` then `409` (never 5xx). Run with
`node exploratory-probe.mjs` against the live backend.

## Ad-hoc QA report
`frontend/e2e/live-test-report.mjs` drives the running stack through the
negative + happy-path scenarios and emits a self-contained HTML report with
full-page screenshots at `docs/qa/live-test-report.html` (gitignored, regenerate
on demand).

## Known limitations exercised honestly
- Mock/seeded findings carry no confidence (`n/a`); real confidence bars require
  the nnU-Net weights wired in.
- The stage timeline is populated from the audit DB; with audit disabled it
  shows "no stage events" while SSE still drives live refresh.
- DICOM-native (OHIF) ingestion is out of scope; viewers are NIfTI/GLB-backed.
