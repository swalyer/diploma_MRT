"""CI guard for the weights manifest (NFR-2: reproducibility).

A weights manifest is only trustworthy if every declared model carries a real
SHA256 digest. This check fails the build when a *real* manifest still contains
`REPLACE_*` / empty placeholders, so production weights can never be wired in
unverified.

Behaviour:

* Real manifest (default ``config/weights_manifest.yml``):
  - missing            -> OK (weights not wired yet; CI has no secrets to fetch)
  - present + placeholder sha256 anywhere -> FAIL
  - present + structurally invalid entry  -> FAIL
* ``--expect-placeholders`` (used to lint the committed *example* template):
  - file must load and parse, placeholders are allowed.

Usage::

    python -m scripts.check_weights_manifest                       # real manifest
    python -m scripts.check_weights_manifest --path config/weights_manifest.example.yml --expect-placeholders
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.weights.manifest import WeightsError, WeightsManager


def check(path: Path, *, expect_placeholders: bool, require_exists: bool) -> int:
    if not path.exists():
        if require_exists or expect_placeholders:
            print(f"FAIL: expected manifest at {path} but it does not exist")
            return 1
        print(f"OK: no real weights manifest at {path}; nothing to verify (weights not wired yet)")
        return 0

    try:
        manager = WeightsManager(path, cache_dir=path.parent / ".weights-cache-lint")
    except WeightsError as exc:
        print(f"FAIL: manifest {path} is structurally invalid: {exc}")
        return 1

    entries = manager.list_entries()
    if not entries:
        print(f"FAIL: manifest {path} declares no models")
        return 1

    placeholders = [entry.key for entry in entries if entry.is_placeholder]
    if expect_placeholders:
        print(f"OK: example manifest {path} parses with {len(entries)} entr(ies) "
              f"({len(placeholders)} placeholder(s), allowed for a template)")
        return 0

    if placeholders:
        print(f"FAIL: real manifest {path} still has placeholder sha256 for: {', '.join(placeholders)}")
        print("      Fill in the real SHA256 digest(s) before enabling these weights.")
        return 1

    print(f"OK: manifest {path} has {len(entries)} entr(ies), all with real SHA256 digests")
    return 0


def main() -> int:
    default_path = Path(__file__).resolve().parents[1] / "config" / "weights_manifest.yml"
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--path", type=Path, default=default_path)
    parser.add_argument("--expect-placeholders", action="store_true",
                        help="Lint a template (example) manifest: must parse, placeholders allowed.")
    parser.add_argument("--require-exists", action="store_true",
                        help="Fail if the manifest file is absent.")
    args = parser.parse_args()
    return check(args.path, expect_placeholders=args.expect_placeholders, require_exists=args.require_exists)


if __name__ == "__main__":
    sys.exit(main())
