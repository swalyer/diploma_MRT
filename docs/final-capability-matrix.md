# Final Capability Matrix

Legend: Implemented / Partial / Missing / Broken / Inferred only

| Area | Capability | Status | Truth note |
|---|---|---|---|
| Startup | Compose topology defined | Implemented | Services/env/volumes declared in `docker-compose.yml`. |
| Startup | Compose runnable proof in this environment | Partial | Docker CLI unavailable in current runtime. |
| Auth | Register/login/JWT | Implemented | Backend + frontend integrated. |
| Cases | Create/list/get/delete ownership-safe | Implemented | `findOwnedCase` gate in service layer. |
| Upload | NIfTI/DICOM/zip acceptance | Implemented | Extension-gated upload + storage object key persistence. |
| Processing | Async orchestration + status transitions | Implemented | Case + inference run transitions persisted. |
| Processing truth | Execution mode exposed | Implemented | Added to status DTO/API/UI. |
| Processing truth | Model version exposed | Implemented | Added to status DTO/API/UI from run record. |
| Processing truth | Metrics surfaced | Implemented | `metricsJson` exposed in status DTO/API. |
| Storage | Artifact download authorization | Implemented | Ownership checked before file read. |
| Storage | Absolute local path leak via API | Implemented (no leak) | API exposes artifact id + download URL only. |
| ML | Mock pipeline | Implemented | Tests passing. |
| ML | CT real path | Partial | Depends on external model binaries/weights. |
| ML | MRI path | Partial/Experimental | Must remain explicitly experimental. |
| Viewer 2D | Artifact-backed NIfTI | Implemented | Production build passes with nifti dependency. |
| Viewer 2D | OHIF DICOM-native | Missing | Not wired end-to-end yet. |
| Viewer 3D | Artifact-backed mesh render | Implemented | Uses actual mesh artifacts only. |
| Viewer 3D | Lesion interaction metadata richness | Partial | Basic presence; advanced interaction limited. |
| UI truth | Contradictory error+confident states | Implemented fix | Error shell suppresses pseudo-certainty. |
| Docs truth | Real vs mock vs experimental | Implemented | README + audit docs explicitly classify. |

## Final reviewer checklist mapping
- Start stack reproducibly: **Partial** (compose defined, not executable in this environment).
- Authenticate: **Implemented**.
- Create case/upload/process: **Implemented in code path**, full smoke requires Docker runtime.
- Truthful status progression: **Implemented**.
- Artifact-backed 2D/3D paths: **Implemented** (NIfTI + mesh).
- Fake completeness removed: **Implemented** for execution mode/model version surfacing and mesh fallback policy.
