Seeded CT demo pack lives here.

- `manifests/` contains typed demo manifest JSON files for admin import.
- `scripts/build_seeded_ct_demo_pack.py` regenerates manifests and lightweight
  demo artifacts from the tiny repository CT smoke fixture already committed in
  `storage/cases/1`.
- `scripts/smoke_seeded_case_api.py` imports a committed manifest into a live
  local stack and verifies seeded read/status/report/findings/viewer/download
  semantics end to end.

The generated assets stay lightweight and artifact-backed:

- source study
- enhanced 3D volume
- liver mask
- lesion mask
- liver mesh
- lesion mesh when findings exist

No heavy external datasets are committed here.
