# Full Readiness Plan

## Purpose

This document defines the implementation plan to bring the project to a fully ready
`demo-ready / thesis-ready / engineering-complete v1` state.

This does not mean medical certification or clinical-grade regulatory readiness.
It means the system is coherent, honest in its claims, architecture-aligned, and
complete enough to be demonstrated and defended without hidden gaps.

## Target State

By the end of this plan, the project must satisfy all of the following:

1. `CT` works end-to-end:
   `create -> upload/import -> process -> findings -> report -> 2D -> 3D -> download -> audit`
2. `MRI` is either:
   `honest-ready` with explicit heuristic limitations, or
   `model-ready` with dedicated validated lesion support.
3. Seeded demo cases and live processed cases are distinct in the domain model.
4. Seeded demo cases reuse the same read path as live cases.
5. Report generation is deterministic, structured, and evidence-bound.
6. 2D and 3D viewers operate on real stored artifacts, not synthetic UI assumptions.
7. Backend remains the only authoritative source of state and capabilities.
8. Demo/import/admin workflows are operable without manual database intervention.
9. Critical flows are covered by unit, integration, and smoke tests.
10. Product claims in UI and docs match actual runtime behavior.

## Non-Goals

This plan does not promise:

1. medical certification
2. clinical-grade diagnostic reliability
3. production-scale distributed hardening beyond the current MVP topology

## Delivery Principle

Implementation should be done in this order:

1. domain truth first
2. contract stability second
3. importer and report consistency third
4. CT completeness fourth
5. MRI honesty or enhancement fifth
6. UI/admin polish sixth
7. validation and docs last, but continuously updated

## Current Reality Summary

At the moment:

1. `CT` is closest to ready and already has a usable end-to-end path.
2. `MRI` is not ready as a full model-backed path; lesion handling is still heuristic.
3. Structured report generation exists, including recommendation text.
4. 3D viewer exists and can show liver and lesion meshes when artifacts are present.
5. The domain still contains architectural debt:
   anemic entities, duplicated artifact semantics, and boundary leakage between backend,
   frontend, and ml-service contracts.

## Phase 0. Definition of Ready

Goal: freeze success criteria so implementation does not drift.

Tasks:

1. Create a capability matrix for `CT`, `MRI`, report, 2D, 3D, demo import, admin flow.
2. Mark each capability as one of:
   `implemented`, `partial`, `heuristic`, `missing`, `not planned`.
3. Decide the mandatory MRI target:
   `honest-ready` minimum or `model-ready` stretch target.
4. Freeze the definition of "full readiness" for thesis/demo scope.

Exit criteria:

1. No ambiguous claims remain in README or UI.
2. MRI target is explicitly chosen.
3. Implementation backlog is traceable to this document.

## Phase 1. Domain Core Repair

Goal: remove the most expensive architectural shortcuts before scaling features.

Tasks:

1. Replace the current anemic `CaseEntity` usage with a proper aggregate or explicit
   domain state machine.
2. Centralize allowed transitions:
   `create`, `upload`, `enqueue`, `complete`, `fail`, `delete`, `import seeded`,
   `recover interrupted`.
3. Move process eligibility rules out of scattered services into one domain-owned place.
4. Centralize origin semantics:
   `LIVE_PROCESSED` vs `SEEDED_DEMO`.
5. Centralize artifact role semantics:
   source artifact, generated artifact, referenced artifact, managed artifact.
6. Remove duplicated source-artifact meanings and define one canonical source study type.

Exit criteria:

1. There is one authoritative state-transition model for cases.
2. Services orchestrate use cases; they do not invent domain rules.
3. Source artifact semantics are not duplicated across backend and frontend.

## Phase 2. Stable Integration Contracts

Goal: stop leaking backend domain objects directly into external service contracts.

Tasks:

1. Introduce a dedicated backend-to-ml integration contract package.
2. Separate external request/response DTOs from internal domain/value objects.
3. Add explicit contract mappers:
   backend domain -> ML request
   ML response -> backend materialization command
4. Stop sharing domain semantics by manual copy across Java, Python, and TypeScript.
5. Decide one contract source of truth:
   OpenAPI-first or generated client/server type flow.

Exit criteria:

1. ML service contract changes do not directly force domain model changes.
2. Frontend types are derived from API contract or kept aligned through a defined process.
3. Contract versioning is explicit.

## Phase 3. Seeded Demo Domain and Importer

Goal: finish the seeded demo pipeline without creating a second architecture.

Tasks:

1. Keep `CaseOrigin`, demo category, provenance, and stable demo identity as first-class domain fields.
2. Keep manifest schema typed and versioned.
3. Confirm the storage reference contract:
   `objectKey` as the only source for managed and referenced artifacts.
4. Make importer idempotent by stable demo identity, not by UI assumptions.
5. Persist seeded cases through the same read model as live cases.
6. Validate required artifacts, checksums, and supported optional artifacts.
7. Audit both import and update actions.
8. Decide and document rerun semantics for seeded cases:
   default should remain disabled unless explicitly redesigned.

Exit criteria:

1. Same manifest can be imported twice without duplicates.
2. Seeded case opens through existing case detail endpoints without hacks.
3. Provenance is visible and trustworthy.

## Phase 4. Deterministic Report Engine v2

Goal: make the report system complete, explainable, and testable.

Tasks:

1. Keep report sections canonical:
   `findings`, `impression`, `limitations`, `recommendation`.
2. Ensure report text is assembled deterministically from structured sections.
3. Ensure recommendation text is evidence-bound and does not invent facts.
4. Reuse the same report model for live and seeded cases.
5. Add tests for:
   no lesions, single lesion, multifocal, MRI heuristic path, seeded import path.
6. Make backend persist structured report data and assembled text as one coherent model.

Exit criteria:

1. Report generator is pure and testable.
2. Reports never claim unsupported certainty.
3. Recommendations are present and conservative.

## Phase 5. CT Complete Path

Goal: close the main product path fully.

Tasks:

1. Ensure `CT real` path produces:
   source volume, enhanced volume if applicable, liver mask, lesion mask,
   liver mesh, lesion mesh when lesions exist, structured findings, report.
2. Improve failure reporting so users know which stage failed:
   validation, liver segmentation, lesion segmentation, mesh generation, report.
3. Confirm 2-3 seeded CT demo cases:
   normal, single lesion, multifocal.
4. Make admin import workflow usable for those CT cases.
5. Add CT smoke tests for:
   upload flow, process flow, seeded import flow, report, 2D, 3D, downloads.

Exit criteria:

1. CT path is defensible as fully working.
2. Demo operator can open prepared CT cases in one or two clicks.
3. Live CT case and seeded CT case behave consistently at read time.

## Phase 6. MRI Decision

Goal: make MRI status honest and complete for the chosen scope.

### Option A. MRI Honest-Ready

Use this as the minimum acceptable target.

Tasks:

1. Keep MRI enabled in UI and API only where behavior is truthful.
2. Mark lesion output as heuristic when dedicated model support is absent.
3. Ensure report limitations explicitly mention heuristic support.
4. Prevent UI from implying clinical-grade MRI lesion detection.
5. Add 1-2 seeded MRI cases that are visually useful and clearly labeled.

Exit criteria:

1. MRI can be demonstrated honestly.
2. No UI or report text overclaims support.

### Option B. MRI Model-Ready

Use this only if there is time and validated model availability.

Tasks:

1. Integrate a dedicated MRI lesion model path.
2. Add MRI-specific metrics and capability reporting.
3. Validate artifacts, findings, and report output on MRI cases.
4. Remove heuristic-only messaging only where model-backed output is confirmed.

Exit criteria:

1. MRI lesion path is model-backed and verified.
2. MRI claims are supported by real runtime behavior and tests.

## Phase 7. 2D Viewer Completion

Goal: make the 2D viewer a dependable diagnostic-support UI layer for the MVP.

Tasks:

1. Keep `NIfTI-only` workflow explicit unless DICOM/OHIF is actually implemented.
2. Improve base volume selection and artifact fallback logic.
3. Add finding-to-slice navigation.
4. Highlight lesion and liver overlays with consistent controls.
5. Handle missing or unreadable artifacts with precise user-facing reasons.
6. Add smoke coverage for seeded and live cases.

Exit criteria:

1. 2D viewer is stable for all supported case types.
2. Findings can be correlated with slices.
3. Viewer behavior is artifact-backed, not inference-by-guess.

## Phase 8. 3D Viewer Completion

Goal: make 3D honest, usable, and demonstrable.

Tasks:

1. Preserve liver mesh and lesion mesh rendering.
2. Add explicit mapping between selected finding and displayed lesion zone.
3. Replace placeholder click behavior with real metadata panel.
4. Show why lesion mesh is absent:
   no lesion, stage failed, unsupported artifact, import incomplete.
5. Keep screenshot/export flow stable.
6. Add smoke coverage for seeded and live cases.

Exit criteria:

1. 3D clearly shows the anatomical mesh and the suspicious zone when available.
2. Selecting a lesion exposes meaningful metadata.
3. Empty states are informative rather than generic.

## Phase 9. Admin and Demo Workflow

Goal: make the system operable without developer intervention.

Tasks:

1. Finish admin-only manifest import UI flow.
2. Add quick access to seeded demo studies.
3. Show seeded/live badges consistently.
4. Show provenance and manifest identity where appropriate.
5. Define rerun behavior for seeded cases explicitly in UI and backend.
6. Make partial-data and degraded states operationally understandable.

Exit criteria:

1. Admin can import or update demo studies safely.
2. Presenter can reach a ready case quickly.
3. Seeded and live cases are never visually conflated.

## Phase 10. Security, Persistence, and Operational Hardening

Goal: remove hidden fragility from the data path.

Tasks:

1. Reflect critical invariants in database constraints and indexes.
2. Centralize ownership checks and remove duplicated access logic.
3. Ensure storage cleanup is safe for managed vs referenced artifacts.
4. Keep audit logging resilient but observable.
5. Improve health and capability endpoints so they reflect actual configured support.
6. Review migration history for legacy compatibility hacks that should be retired.

Exit criteria:

1. Important domain invariants are enforced below the service layer as well.
2. Runtime failures surface clear operational reasons.
3. Access control and cleanup behavior are consistent.

## Phase 11. Test Matrix

Goal: make readiness measurable.

Required tests:

1. backend domain/service tests
2. importer unit tests
3. importer idempotency tests
4. importer checksum validation tests
5. seeded case API smoke
6. live case API smoke
7. CT process flow smoke
8. MRI honest-ready smoke
9. report rendering smoke
10. 2D viewer smoke
11. 3D viewer smoke
12. artifact download smoke
13. admin import UI smoke

Exit criteria:

1. Every critical flow has at least one automated verification path.
2. Regressions in seeded/live parity are caught automatically.

## Phase 12. Documentation and Defense Materials

Goal: align the implementation with the story that will be presented.

Tasks:

1. Update README capability claims.
2. Maintain a final implemented-vs-partial-vs-missing matrix.
3. Write a demo script:
   login, seeded case open, live CT upload/process, report review, 2D, 3D, provenance.
4. Document MRI limitations honestly.
5. Document seeded demo import workflow.
6. Document known limitations and future work.

Exit criteria:

1. Presentation materials match the real system.
2. There are no hidden disclaimers known only to developers.

## Recommended Execution Order

1. Phase 0
2. Phase 1
3. Phase 2
4. Phase 3
5. Phase 4
6. Phase 5
7. Phase 6
8. Phase 7
9. Phase 8
10. Phase 9
11. Phase 10
12. Phase 11
13. Phase 12

## Immediate Priority Backlog

These are the first items to execute before any UI polish:

1. repair case domain state model
2. remove duplicated source artifact semantics
3. isolate ML integration contracts from domain types
4. finish importer idempotency and manifest semantics
5. harden deterministic report generation
6. lock CT as the primary fully working path
7. choose MRI target explicitly

## Definition of Done

The initiative is done only when all are true:

1. CT is fully working and demonstrable end-to-end.
2. MRI is either honest-ready or model-ready, with claims matching reality.
3. Seeded and live cases are distinct in the domain but unified in the read path.
4. Reports are structured, deterministic, and evidence-bound.
5. 2D viewer is stable on supported artifacts.
6. 3D viewer shows liver and suspicious zone when lesion mesh exists.
7. Admin import workflow is safe and repeatable.
8. Critical flows are covered by automated tests.
9. README, UI, and runtime capabilities say the same thing.

## Implementation Checkpoint

Implemented so far:

1. `Phase 1` domain-state slice:
   `CaseEntity` now owns live creation, seeded import, and core status transitions
   (`upload`, `process`, `complete`, `fail`, `delete guard`) instead of scattering them across services.
2. `Phase 1` source-artifact normalization:
   the duplicate `ORIGINAL_INPUT` meaning was removed from the domain code path;
   `ORIGINAL_STUDY` is now the canonical source study artifact type and a DB migration normalizes legacy rows.
3. `Phase 3` read-path parity improvement:
   seeded case read APIs now expose explicit `resultReady` / `resultSource` semantics,
   so case detail pages no longer depend on synthetic completed `InferenceRun` rows
   to open imported demo studies.
4. `Phase 9` operability slice:
   seeded demo cases are now readable for authenticated users, not only the importing admin;
   admin summary exposes ready demo studies for quick access.
5. `Phase 9` admin UI slice:
   frontend admin page now supports manifest JSON import submission and renders quick links
   to imported seeded demo studies.
6. `Phase 9` mutation semantics slice:
   seeded demo cases now have explicit write semantics:
   read is shared for authenticated users, but mutation remains admin-only for seeded demos
   while live case mutation stays owner-bound.
7. targeted backend verification:
   focused tests for seeded read/mutation access, state transitions, and demo import read-model behavior
   are now green in the local backend Maven run.
8. `Phase 2` ML integration boundary slice:
   runtime ML request/response handling now uses dedicated integration-contract DTOs and explicit
   contract-to-domain mapping instead of reusing domain/value objects as the wire format.
9. importer hardening slice:
   `DemoCaseImportService` now enforces bean-validation constraints at the service boundary as well,
   and the importer test matrix covers bean-validation failure, missing lesion mesh, and invalid objectKey rejection.
10. seeded read-model cleanup slice:
   importer no longer creates synthetic seeded `InferenceRun` rows, list/status mapping ignores
   legacy seeded compatibility runs, and a follow-up migration retires those rows from persistence.

Still next:

1. harden remaining importer/report/test matrix coverage
2. close remaining admin/demo UX and documentation gaps
3. decide whether Phase 5 MRI seeded cases are honest-ready or should stay deferred
