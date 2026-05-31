"""nnU-Net v2 lesion-segmentation adapter.

Runs real inference through the nnU-Net Python ``nnUNetPredictor`` API and
resolves its weights through the :class:`WeightsManager` (mandatory SHA256
verification, NFR-2). The trained model can be provided two ways:

1. A local nnU-Net results folder (``model_dir_by_modality``) — the path the
   diploma uses straight after training on this machine.
2. A verified weights bundle declared in the manifest
   (``weights_key_by_modality``) — the reproducible/distributable path.

Anything that prevents real inference (no model configured, placeholder
sha256, missing checkpoint, runtime error) degrades *explicitly* to the
heuristic fallback rather than failing the request (NFR-4). The caller learns
which path ran via :class:`LesionSegmentation`.

nnU-Net imports are deliberately lazy so importing this module never triggers
nnU-Net's heavy import side effects or path warnings.
"""
from __future__ import annotations

import logging
import os
import shutil
import tempfile
from dataclasses import dataclass, field
from pathlib import Path

import nibabel as nib
import numpy as np

from app.adapters.lesion import LesionSegmentation
from app.postprocessing.heuristic_segmentation import evidence_key_for, save_heuristic_lesion_mask
from app.weights.manifest import WeightsError, WeightsManager, ResolvedWeights

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class NnUnetAdapterConfig:
    """Everything the adapter needs that is not request-scoped."""

    device_preference: str = "auto"          # auto | cpu | cuda
    folds: tuple[str, ...] = ("0",)
    checkpoint: str = "checkpoint_final.pth"
    results_cache_dir: str | None = None     # where manifest bundles are installed
    model_dir_by_modality: dict[str, str] = field(default_factory=dict)
    weights_key_by_modality: dict[str, str] = field(default_factory=dict)
    # Which class in a multi-class model is the lesion/tumour. The ATLAS model
    # is {0:bg, 1:liver, 2:tumour}, so the lesion mask is label 2 — copying the
    # whole prediction would mislabel the entire liver as a lesion.
    lesion_label: int = 2


@dataclass(frozen=True)
class _ResolvedModel:
    model_dir: Path
    name: str


class NnUnetAdapter:
    def __init__(self, config: NnUnetAdapterConfig, weights_manager: WeightsManager | None = None):
        self.config = config
        self.weights_manager = weights_manager

    def segment_lesion(
        self,
        input_key: str,
        liver_mask_key: str,
        output_key: str,
        artifacts_root: str,
        modality: str,
    ) -> LesionSegmentation:
        modality_value = getattr(modality, "value", modality)
        in_path = Path(artifacts_root) / input_key
        out_path = Path(artifacts_root) / output_key
        out_path.parent.mkdir(parents=True, exist_ok=True)

        model = self._resolve_model(modality_value)
        if model is not None:
            try:
                device, device_label = _resolve_device(self.config.device_preference)
                produced = self._run_predictor(in_path, out_path, output_key, artifacts_root, model, device)
                if produced:
                    logger.info(
                        "nnU-Net lesion inference succeeded model=%s device=%s input=%s",
                        model.name, device_label, input_key,
                    )
                    return LesionSegmentation(
                        object_key=output_key, is_model=True, model_name=model.name, device=device_label,
                    )
                logger.warning("nnU-Net produced no output for %s, falling back to heuristic", input_key)
            except Exception as exc:  # noqa: BLE001 — never let inference break the request
                logger.warning("nnU-Net inference failed, falling back to heuristic", exc_info=exc)

        key, _ = save_heuristic_lesion_mask(input_key, liver_mask_key, output_key, artifacts_root, modality_value)
        return LesionSegmentation.heuristic(key)

    def configured_models(self) -> dict[str, str]:
        """Modality -> model name for models that could run without downloading.

        Cheap, side-effect-free check used by the health endpoint: a local model
        dir that exists, or a manifest weights key with a non-placeholder digest.
        """
        out: dict[str, str] = {}
        for modality_value, model_dir in self.config.model_dir_by_modality.items():
            if model_dir and Path(model_dir).exists():
                out[modality_value] = self._derive_name(Path(model_dir), modality_value)
        for modality_value, key in self.config.weights_key_by_modality.items():
            if modality_value in out or not key or self.weights_manager is None:
                continue
            try:
                entry = self.weights_manager.get(key)
                if not entry.is_placeholder:
                    out[modality_value] = entry.name
            except WeightsError:
                continue
        return out

    # -- model resolution -------------------------------------------------

    def _resolve_model(self, modality_value: str) -> _ResolvedModel | None:
        local_dir = self.config.model_dir_by_modality.get(modality_value)
        if local_dir:
            path = Path(local_dir)
            if path.exists():
                return _ResolvedModel(model_dir=path, name=self._derive_name(path, modality_value))
            logger.warning("Configured nnU-Net model dir for %s does not exist: %s", modality_value, path)

        key = self.config.weights_key_by_modality.get(modality_value)
        if key and self.weights_manager is not None:
            try:
                resolved = self.weights_manager.resolve(key)
                model_dir = self._install_bundle(resolved)
                if model_dir is not None:
                    return _ResolvedModel(model_dir=model_dir, name=resolved.entry.name)
            except WeightsError as exc:
                logger.warning("nnU-Net weights '%s' unavailable: %s", key, exc)
        return None

    def _install_bundle(self, resolved: ResolvedWeights) -> Path | None:
        """Install a verified nnU-Net export zip into the results cache and
        return the trainer folder for ``initialize_from_trained_model_folder``."""
        extra = resolved.entry.extra
        dataset_name = extra.get("dataset_name")
        if not dataset_name:
            logger.warning("Weights entry '%s' lacks dataset_name; cannot locate model dir", resolved.entry.key)
            return None
        trainer = extra.get("trainer", "nnUNetTrainer")
        plans = extra.get("plans", "nnUNetPlans")
        config = extra.get("nn_config", "3d_fullres")
        results_root = Path(self.config.results_cache_dir or (self.weights_manager.cache_dir / "nnUNet_results"))
        model_dir = results_root / dataset_name / f"{trainer}__{plans}__{config}"
        if not model_dir.exists():
            results_root.mkdir(parents=True, exist_ok=True)
            os.environ.setdefault("nnUNet_raw", str(results_root.parent / "nnUNet_raw"))
            os.environ.setdefault("nnUNet_preprocessed", str(results_root.parent / "nnUNet_preprocessed"))
            os.environ["nnUNet_results"] = str(results_root)
            from nnunetv2.model_sharing.model_import import install_model_from_zip_file
            install_model_from_zip_file(str(resolved.local_path))
        return model_dir if model_dir.exists() else None

    def _derive_name(self, model_dir: Path, modality_value: str) -> str:
        # <results>/<DatasetXXX_Name>/<trainer__plans__config>
        dataset = model_dir.parent.name or f"nnUNet_{modality_value}"
        return f"nnU-Net v2 [{dataset}]"

    # -- inference --------------------------------------------------------

    def _run_predictor(
        self,
        in_path: Path,
        out_path: Path,
        output_key: str,
        artifacts_root: str,
        model: _ResolvedModel,
        device,
    ) -> bool:
        from nnunetv2.inference.predict_from_raw_data import nnUNetPredictor

        with tempfile.TemporaryDirectory(prefix="nnunet_infer_") as tmp:
            tmp_in = Path(tmp) / "in"
            tmp_out = Path(tmp) / "out"
            tmp_in.mkdir()
            tmp_out.mkdir()
            stem = in_path.name.replace(".nii.gz", "").replace(".nii", "")
            # nnU-Net expects channel-suffixed inputs: <case>_0000.nii.gz
            shutil.copy2(in_path, tmp_in / f"{stem}_0000.nii.gz")

            predictor = nnUNetPredictor(
                tile_step_size=0.5,
                use_gaussian=True,
                use_mirroring=True,
                perform_everything_on_device=(device.type == "cuda"),
                device=device,
                verbose=False,
                verbose_preprocessing=False,
                allow_tqdm=False,
            )
            predictor.initialize_from_trained_model_folder(
                str(model.model_dir),
                use_folds=tuple(self.config.folds),
                checkpoint_name=self.config.checkpoint,
            )
            predictor.predict_from_files(
                [[str(tmp_in / f"{stem}_0000.nii.gz")]],
                str(tmp_out),
                save_probabilities=True,
                overwrite=True,
                num_processes_preprocessing=1,
                num_processes_segmentation_export=1,
            )

            pred = tmp_out / f"{stem}.nii.gz"
            if not pred.exists():
                return False
            self._save_lesion_mask(pred, out_path)
            self._export_evidence(tmp_out / f"{stem}.npz", out_path, output_key, artifacts_root)
            return True

    def _save_lesion_mask(self, pred_path: Path, out_path: Path) -> None:
        """Extract the lesion class from the (possibly multi-class) prediction.

        For the 3-class ATLAS model only label 2 (tumour) is the lesion; for a
        binary lesion-only model the single foreground class is used.
        """
        image = nib.load(str(pred_path))
        data = np.asanyarray(image.dataobj)
        label = self.config.lesion_label
        mask = data == label
        if not mask.any() and int(data.max(initial=0)) == 1 and label != 1:
            # Binary foreground model — the single positive class is the lesion.
            mask = data > 0
        nib.save(nib.Nifti1Image(mask.astype(np.uint8), image.affine, image.header), str(out_path))

    def _export_evidence(self, npz_path: Path, mask_path: Path, output_key: str, artifacts_root: str) -> None:
        """Persist the lesion-class softmax probability as evidence so per-finding
        confidence (FR-7) is model-derived, mirroring the heuristic evidence file."""
        if not npz_path.exists():
            return
        try:
            mask_image = nib.load(str(mask_path))
            probabilities = np.load(npz_path)["probabilities"]
            label = self.config.lesion_label
            # Use the lesion-class channel when present; else fall back to 1-bg.
            channel = probabilities[label] if probabilities.shape[0] > label else (1.0 - probabilities[0])
            foreground = np.asarray(channel, dtype=np.float32)
            mask_shape = mask_image.shape
            if foreground.shape != mask_shape:
                if foreground.T.shape == mask_shape:
                    foreground = np.ascontiguousarray(foreground.T)
                else:
                    logger.info("nnU-Net probability shape %s != mask %s; skipping evidence map",
                                foreground.shape, mask_shape)
                    return
            evidence_path = Path(artifacts_root) / evidence_key_for(output_key)
            nib.save(nib.Nifti1Image(foreground, mask_image.affine, mask_image.header), str(evidence_path))
        except Exception as exc:  # noqa: BLE001 — evidence is best-effort, mask already saved
            logger.info("Could not export nnU-Net probability evidence: %s", exc)


def _resolve_device(preference: str):
    """Return (torch.device, label). 'auto' picks cuda when available."""
    import torch

    pref = (preference or "auto").lower()
    cuda_ok = torch.cuda.is_available()
    if pref == "cpu" or (pref in {"cuda", "gpu"} and not cuda_ok):
        if pref in {"cuda", "gpu"} and not cuda_ok:
            logger.warning("CUDA requested but unavailable; running nnU-Net on CPU")
        return torch.device("cpu"), "cpu"
    if pref in {"cuda", "gpu"} or cuda_ok:
        return torch.device("cuda", 0), "cuda"
    return torch.device("cpu"), "cpu"
