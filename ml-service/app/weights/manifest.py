"""Weights manifest loader with mandatory SHA256 verification.

The manifest is the single source of truth for which model weights are
allowed at runtime. Adapters never download or pin checkpoints by hand —
they ask the manager for a verified local path and let it fail loud on
checksum mismatch or missing artifact.
"""
from __future__ import annotations

import hashlib
import logging
import os
import urllib.request
from dataclasses import dataclass
from pathlib import Path

import yaml

logger = logging.getLogger(__name__)


class WeightsError(RuntimeError):
    """Raised on missing, unreachable, or checksum-mismatching weights."""


@dataclass(frozen=True)
class WeightsEntry:
    key: str
    name: str
    version: str
    archive: str
    url: str
    sha256: str
    framework: str
    purpose: str
    modality: str
    extra: dict

    @property
    def is_placeholder(self) -> bool:
        return self.sha256.startswith("REPLACE_") or not self.sha256


@dataclass
class ResolvedWeights:
    entry: WeightsEntry
    local_path: Path


class WeightsManager:
    def __init__(self, manifest_path: Path | str, cache_dir: Path | str):
        self.manifest_path = Path(manifest_path)
        self.cache_dir = Path(cache_dir)
        self.cache_dir.mkdir(parents=True, exist_ok=True)
        self._entries = self._load_manifest()

    def list_entries(self) -> list[WeightsEntry]:
        return list(self._entries.values())

    def get(self, key: str) -> WeightsEntry:
        if key not in self._entries:
            raise WeightsError(f"Weights key '{key}' not declared in manifest {self.manifest_path}")
        return self._entries[key]

    def resolve(self, key: str, *, allow_download: bool = True) -> ResolvedWeights:
        entry = self.get(key)
        if entry.is_placeholder:
            raise WeightsError(
                f"Weights '{key}' have a placeholder sha256 ({entry.sha256!r}). "
                f"Update {self.manifest_path} with the real digest before enabling the adapter."
            )

        local_path = self.cache_dir / entry.archive
        if local_path.exists():
            self._verify_sha256(local_path, entry)
            return ResolvedWeights(entry=entry, local_path=local_path)

        if not allow_download:
            raise WeightsError(f"Weights '{key}' not present at {local_path} and downloads are disabled.")
        self._download(entry.url, local_path)
        self._verify_sha256(local_path, entry)
        return ResolvedWeights(entry=entry, local_path=local_path)

    def _load_manifest(self) -> dict[str, WeightsEntry]:
        if not self.manifest_path.exists():
            logger.info("Weights manifest not present at %s — manager starts empty.", self.manifest_path)
            return {}
        raw = yaml.safe_load(self.manifest_path.read_text()) or {}
        models = raw.get("models", {})
        out: dict[str, WeightsEntry] = {}
        for key, body in models.items():
            try:
                entry = WeightsEntry(
                    key=key,
                    name=body["name"],
                    version=body["version"],
                    archive=body["archive"],
                    url=body["url"],
                    sha256=body.get("sha256", ""),
                    framework=body.get("framework", "unknown"),
                    purpose=body.get("purpose", "unspecified"),
                    modality=body.get("modality", "ANY"),
                    extra={k: v for k, v in body.items() if k not in {"name", "version", "archive", "url", "sha256",
                                                                       "framework", "purpose", "modality"}},
                )
            except KeyError as missing:
                raise WeightsError(f"Manifest entry '{key}' is missing required field {missing}") from missing
            out[key] = entry
        return out

    def _download(self, url: str, destination: Path) -> None:
        logger.info("Downloading weights from %s to %s", url, destination)
        destination.parent.mkdir(parents=True, exist_ok=True)
        tmp_path = destination.with_suffix(destination.suffix + ".part")
        try:
            with urllib.request.urlopen(url) as response, tmp_path.open("wb") as fh:
                while chunk := response.read(1 << 20):
                    fh.write(chunk)
            os.replace(tmp_path, destination)
        except Exception as exc:
            if tmp_path.exists():
                tmp_path.unlink(missing_ok=True)
            raise WeightsError(f"Failed to download weights from {url}: {exc}") from exc

    @staticmethod
    def _verify_sha256(path: Path, entry: WeightsEntry) -> None:
        digest = hashlib.sha256()
        with path.open("rb") as fh:
            while chunk := fh.read(1 << 20):
                digest.update(chunk)
        actual = digest.hexdigest()
        if actual.lower() != entry.sha256.lower():
            raise WeightsError(
                f"SHA256 mismatch for '{entry.key}' at {path}: expected {entry.sha256}, got {actual}. "
                f"Refusing to load potentially tampered weights."
            )
