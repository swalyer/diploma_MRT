"""Shared value object for lesion-segmentation adapter results.

Adapters report not just *where* the lesion mask is, but *what* produced it,
so the pipeline can self-report honestly (real model vs heuristic fallback,
which model, on which device) per NFR-3/NFR-4.
"""
from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class LesionSegmentation:
    object_key: str
    is_model: bool
    model_name: str | None = None
    device: str | None = None

    @classmethod
    def heuristic(cls, object_key: str) -> "LesionSegmentation":
        return cls(object_key=object_key, is_model=False, model_name=None, device=None)
