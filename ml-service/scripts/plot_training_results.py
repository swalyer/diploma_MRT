"""Generate publication-quality figures for diploma from nnU-Net training results.

Produces:
  1. loss_curves.png        — train/val loss per epoch (all folds overlaid)
  2. dice_distribution.png  — box plot of val Dice per fold
  3. metrics_comparison.png — bar chart: heuristic baseline vs nnU-Net (from eval JSON)
  4. per_case_scatter.png   — scatter: Dice vs lesion volume for each test case
  5. summary_table.png      — rendered table of aggregate metrics (diploma-ready)

Usage (after training fold 0+):
    python scripts/plot_training_results.py \
        --results-dir $nnUNet_results/Dataset501_AtlasMRILesion/nnUNetTrainer__nnUNetPlans__3d_fullres \
        --eval-json  docs/eval_atlas_fold0.json \
        --eval-csv   docs/eval_atlas_fold0_per_case.csv \
        --baseline-json docs/eval_heuristic_baseline.json \
        --out-dir    docs/figures
"""
from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
import numpy as np

# ── style ──────────────────────────────────────────────────────────────────
PALETTE = ["#1f77b4", "#ff7f0e", "#2ca02c", "#d62728", "#9467bd"]
plt.rcParams.update({
    "font.family": "DejaVu Sans",
    "font.size": 11,
    "axes.titlesize": 13,
    "axes.titleweight": "bold",
    "figure.dpi": 150,
    "savefig.dpi": 200,
    "savefig.bbox": "tight",
    "axes.spines.top": False,
    "axes.spines.right": False,
})


# ── helpers ─────────────────────────────────────────────────────────────────

def _parse_nnunet_log(log_path: Path) -> tuple[list[float], list[float]]:
    """Return (train_losses, val_losses) from nnU-Net training_log_*.txt."""
    train, val = [], []
    for line in log_path.read_text(encoding="utf-8", errors="ignore").splitlines():
        m = re.search(r"train_loss\s+(-?[\d.eE+\-]+)", line)
        if m:
            train.append(float(m.group(1)))
        m = re.search(r"val_loss\s+(-?[\d.eE+\-]+)", line)
        if m:
            val.append(float(m.group(1)))
    return train, val


def _parse_all_logs_by_epoch(fold_dir: Path) -> dict[int, dict]:
    """Parse every training_log_*.txt in a fold dir, keyed by true epoch number.

    Training was paused/resumed several times, producing multiple log files with
    overlapping epoch ranges. We track the current epoch from "Epoch N" headers
    and attach the train_loss / val_loss / per-class pseudo-Dice that follow it.
    Later files overwrite earlier ones for the same epoch (resume re-runs a few
    epochs), so the result is a clean, de-duplicated epoch -> metrics map.
    """
    epoch_re = re.compile(r":\s*Epoch (\d+)\s*$")
    train_re = re.compile(r"train_loss\s+(-?[\d.eE+\-]+)")
    val_re = re.compile(r"val_loss\s+(-?[\d.eE+\-]+)")
    dice_re = re.compile(r"Pseudo dice\s*\[([^\]]+)\]")

    by_epoch: dict[int, dict] = {}
    for log_path in sorted(fold_dir.glob("training_log_*.txt")):
        current = None
        for line in log_path.read_text(encoding="utf-8", errors="ignore").splitlines():
            em = epoch_re.search(line)
            if em:
                current = int(em.group(1))
                by_epoch.setdefault(current, {})
                continue
            if current is None:
                continue
            tm = train_re.search(line)
            if tm:
                by_epoch[current]["train"] = float(tm.group(1))
            vm = val_re.search(line)
            if vm:
                by_epoch[current]["val"] = float(vm.group(1))
            dm = dice_re.search(line)
            if dm:
                nums = re.findall(r"-?\d+\.?\d*(?:[eE][+\-]?\d+)?", dm.group(1))
                vals = [float(n) for n in nums]
                if len(vals) >= 2:
                    by_epoch[current]["dice_liver"] = vals[0]
                    by_epoch[current]["dice_tumour"] = vals[1]
    return by_epoch


def _load_eval(json_path: Path | None) -> dict:
    if json_path and json_path.exists():
        return json.loads(json_path.read_text())
    return {}


def _load_per_case_csv(csv_path: Path | None) -> list[dict]:
    if csv_path and csv_path.exists():
        with csv_path.open() as fh:
            return list(csv.DictReader(fh))
    return []


# ── plot 1: loss curves ──────────────────────────────────────────────────────

def plot_loss_curves(results_dir: Path, out_dir: Path) -> None:
    """Two panels by TRUE epoch number (0-999), stitched across all resume logs:
    left = train/val loss, right = per-class pseudo-Dice (liver, tumour)."""
    fold_dirs = sorted(results_dir.glob("fold_*"))
    fig, (ax_loss, ax_dice) = plt.subplots(1, 2, figsize=(14, 5))
    found_any = False

    for fold_dir in fold_dirs:
        fold = fold_dir.name.replace("fold_", "")
        by_epoch = _parse_all_logs_by_epoch(fold_dir)
        if not by_epoch:
            continue
        found_any = True
        epochs = sorted(by_epoch)
        color = PALETTE[int(fold) % len(PALETTE)]

        train = [(e, by_epoch[e]["train"]) for e in epochs if "train" in by_epoch[e]]
        val = [(e, by_epoch[e]["val"]) for e in epochs if "val" in by_epoch[e]]
        if train:
            ax_loss.plot(*zip(*train), color=color, linewidth=1.3, alpha=0.9, label=f"Fold {fold} train")
        if val:
            ax_loss.plot(*zip(*val), color=color, linestyle="--", linewidth=1.1, alpha=0.65, label=f"Fold {fold} val")

        liver = [(e, by_epoch[e]["dice_liver"]) for e in epochs if "dice_liver" in by_epoch[e]]
        tumour = [(e, by_epoch[e]["dice_tumour"]) for e in epochs if "dice_tumour" in by_epoch[e]]
        if liver:
            ax_dice.plot(*zip(*liver), color="#2ca02c", linewidth=1.2, alpha=0.85, label="liver")
        if tumour:
            ax_dice.plot(*zip(*tumour), color="#d62728", linewidth=1.0, alpha=0.7, label="tumour")

    if not found_any:
        print("[WARN] No training logs found — skipping loss_curves.png")
        plt.close(fig)
        return

    ax_loss.set_xlabel("Epoch")
    ax_loss.set_ylabel("Loss (CE + soft Dice)")
    ax_loss.set_title("Training / Validation Loss")
    ax_loss.legend(fontsize=9, framealpha=0.5)
    ax_loss.grid(linestyle=":", alpha=0.5)

    ax_dice.set_xlabel("Epoch")
    ax_dice.set_ylabel("Pseudo-Dice (validation)")
    ax_dice.set_title("Per-class Pseudo-Dice during training")
    ax_dice.set_ylim(0, 1.0)
    ax_dice.legend(fontsize=9, framealpha=0.5)
    ax_dice.grid(linestyle=":", alpha=0.5)

    fig.suptitle("nnU-Net 3d_fullres — Dataset 501 (ATLAS liver+tumour), fold 0", fontweight="bold")
    out = out_dir / "loss_curves.png"
    fig.savefig(out)
    plt.close(fig)
    print(f"[OK] {out}")


# ── plot 2: dice distribution per fold ───────────────────────────────────────

def plot_dice_distribution(results_dir: Path, out_dir: Path) -> None:
    """Per-class final-validation Dice box plot, read from nnU-Net summary.json.

    nnU-Net v2 structure: summary['metric_per_case'] is a LIST; each entry has
    ['metrics'][label]['Dice'] keyed by label id ('1' liver, '2' tumour)."""
    label_names = {"1": "liver", "2": "tumour"}
    per_class: dict[str, list[float]] = {}

    for fold_dir in sorted(results_dir.glob("fold_*")):
        summary_path = fold_dir / "validation" / "summary.json"
        if not summary_path.exists():
            continue
        data = json.loads(summary_path.read_text())
        for case in data.get("metric_per_case", []):
            metrics = case.get("metrics", {})
            for label, name in label_names.items():
                d = metrics.get(label, {}).get("Dice")
                if d is not None and not (isinstance(d, float) and d != d):  # skip NaN
                    per_class.setdefault(name, []).append(float(d))

    per_class = {k: v for k, v in per_class.items() if v}
    if not per_class:
        print("[WARN] No validation summary.json found — skipping dice_distribution.png")
        return

    names = list(per_class)
    data_cols = [per_class[n] for n in names]
    colors = {"liver": "#2ca02c", "tumour": "#d62728"}

    fig, ax = plt.subplots(figsize=(2 + len(names) * 1.8, 5))
    bp = ax.boxplot(data_cols, patch_artist=True, widths=0.55,
                    medianprops=dict(color="black", linewidth=2))
    for patch, name in zip(bp["boxes"], names):
        patch.set_facecolor(colors.get(name, "#1f77b4"))
        patch.set_alpha(0.6)
    # overlay individual cases
    for i, col in enumerate(data_cols, start=1):
        x = np.random.normal(i, 0.05, size=len(col))
        ax.scatter(x, col, s=22, color="black", alpha=0.5, zorder=3)

    ax.set_xticks(range(1, len(names) + 1))
    ax.set_xticklabels([f"{n}\n(mean {np.mean(per_class[n]):.3f})" for n in names])
    ax.set_ylabel("Dice Score")
    ax.set_title("Final Validation Dice per Class — fold 0 (12 cases)")
    ax.set_ylim(0, 1.05)
    ax.axhline(0.45, color="gray", linestyle=":", alpha=0.7, label="thesis bar = 0.45")
    ax.legend(fontsize=9)
    ax.grid(axis="y", linestyle=":", alpha=0.5)

    out = out_dir / "dice_distribution.png"
    fig.savefig(out)
    plt.close(fig)
    print(f"[OK] {out}")


# ── plot 3: metrics comparison heuristic vs nnU-Net ─────────────────────────

def plot_metrics_comparison(eval_json: Path | None, baseline_json: Path | None, out_dir: Path) -> None:
    nnunet = _load_eval(eval_json)
    baseline = _load_eval(baseline_json)

    if not nnunet and not baseline:
        print("[WARN] No eval JSON files found — skipping metrics_comparison.png")
        return

    metrics_keys = ["dice_mean", "sensitivity_mean", "fp_mean", "hd95_mm_mean"]
    labels = ["Dice (mean)", "Sensitivity (mean)", "FP / case (mean)", "HD95, mm (mean)"]
    higher_is_better = [True, True, False, False]

    fig, axes = plt.subplots(1, len(metrics_keys), figsize=(14, 5))
    fig.suptitle("Segmentation Quality: Heuristic Baseline vs nnU-Net", fontsize=13, fontweight="bold")

    for ax, key, label, hib in zip(axes, metrics_keys, labels, higher_is_better):
        vals, names, colors = [], [], []
        if baseline.get(key) is not None:
            vals.append(float(baseline[key]))
            names.append("Heuristic")
            colors.append("#aec7e8")
        if nnunet.get(key) is not None:
            vals.append(float(nnunet[key]))
            names.append("nnU-Net")
            colors.append("#1f77b4")

        if not vals:
            ax.text(0.5, 0.5, "N/A", ha="center", va="center", transform=ax.transAxes)
            ax.set_title(label)
            continue

        bars = ax.bar(names, vals, color=colors, edgecolor="white", linewidth=0.8)
        for bar, v in zip(bars, vals):
            ax.text(bar.get_x() + bar.get_width() / 2, bar.get_height() + 0.01,
                    f"{v:.3f}", ha="center", va="bottom", fontsize=10, fontweight="bold")

        ax.set_title(label)
        ax.set_ylim(0, max(vals) * 1.25 + 0.05)
        ax.grid(axis="y", linestyle=":", alpha=0.5)

        if len(vals) == 2:
            delta = vals[1] - vals[0]
            sign = "+" if delta > 0 else ""
            color = "green" if (hib and delta > 0) or (not hib and delta < 0) else "red"
            ax.text(0.97, 0.97, f"Δ {sign}{delta:.3f}", transform=ax.transAxes,
                    ha="right", va="top", fontsize=10, color=color, fontweight="bold")

    out = out_dir / "metrics_comparison.png"
    fig.savefig(out)
    plt.close(fig)
    print(f"[OK] {out}")


# ── plot 4: per-case scatter — Dice vs lesion count ─────────────────────────

def plot_per_case_scatter(eval_csv: Path | None, out_dir: Path) -> None:
    rows = _load_per_case_csv(eval_csv)
    if not rows:
        print("[WARN] No per-case CSV — skipping per_case_scatter.png")
        return

    dices = [float(r["dice"]) for r in rows]
    gt_counts = [int(r["gt_lesion_count"]) for r in rows]
    fp_counts = [float(r["fp_per_case"]) for r in rows]
    case_ids = [r["case_id"] for r in rows]

    fig, axes = plt.subplots(1, 2, figsize=(13, 5))
    fig.suptitle("Per-Case Segmentation Results — nnU-Net (ATLAS)", fontweight="bold")

    # left: Dice vs lesion count
    ax = axes[0]
    sc = ax.scatter(gt_counts, dices, c=fp_counts, cmap="RdYlGn_r",
                    s=60, edgecolors="gray", linewidth=0.5, alpha=0.85, vmin=0, vmax=5)
    plt.colorbar(sc, ax=ax, label="FP / case")
    ax.axhline(0.5, color="gray", linestyle=":", alpha=0.6)
    ax.set_xlabel("GT Lesion Count")
    ax.set_ylabel("Dice Score")
    ax.set_title("Dice vs Lesion Count\n(color = FP/case)")
    ax.set_ylim(-0.05, 1.05)
    ax.grid(linestyle=":", alpha=0.4)

    # right: sorted bar chart of per-case Dice
    ax2 = axes[1]
    order = np.argsort(dices)
    sorted_dices = [dices[i] for i in order]
    sorted_ids = [case_ids[i] for i in order]
    colors = ["#d62728" if d < 0.5 else "#2ca02c" for d in sorted_dices]
    ax2.barh(range(len(sorted_dices)), sorted_dices, color=colors, edgecolor="none", height=0.8)
    ax2.axvline(0.5, color="gray", linestyle=":", linewidth=1.2)
    ax2.set_yticks(range(len(sorted_ids)))
    if len(sorted_ids) <= 30:
        ax2.set_yticklabels(sorted_ids, fontsize=8)
    else:
        ax2.set_yticks([])
    ax2.set_xlabel("Dice Score")
    ax2.set_title("Per-Case Dice (sorted)\n(red < 0.5, green ≥ 0.5)")
    ax2.set_xlim(0, 1.05)
    ax2.grid(axis="x", linestyle=":", alpha=0.4)

    out = out_dir / "per_case_scatter.png"
    fig.savefig(out)
    plt.close(fig)
    print(f"[OK] {out}")


# ── plot 5: summary table ────────────────────────────────────────────────────

def plot_summary_table(eval_json: Path | None, baseline_json: Path | None, out_dir: Path) -> None:
    nnunet = _load_eval(eval_json)
    baseline = _load_eval(baseline_json)

    if not nnunet and not baseline:
        print("[WARN] No eval data — skipping summary_table.png")
        return

    def fmt(d: dict, key: str) -> str:
        v = d.get(key)
        return "—" if v is None else f"{float(v):.3f}"

    columns = ["Model", "Cases", "Dice (mean ± std)", "Sensitivity", "FP / case", "HD95 (mm)"]
    rows = []
    if baseline:
        std = f"± {float(baseline.get('dice_std', 0)):.3f}" if baseline.get("dice_std") else ""
        rows.append([
            "Heuristic baseline",
            str(baseline.get("cases", "—")),
            f"{fmt(baseline, 'dice_mean')} {std}",
            fmt(baseline, "sensitivity_mean"),
            fmt(baseline, "fp_mean"),
            fmt(baseline, "hd95_mm_mean"),
        ])
    if nnunet:
        std = f"± {float(nnunet.get('dice_std', 0)):.3f}" if nnunet.get("dice_std") else ""
        rows.append([
            "nnU-Net 3d_fullres",
            str(nnunet.get("cases", "—")),
            f"{fmt(nnunet, 'dice_mean')} {std}",
            fmt(nnunet, "sensitivity_mean"),
            fmt(nnunet, "fp_mean"),
            fmt(nnunet, "hd95_mm_mean"),
        ])

    fig, ax = plt.subplots(figsize=(12, max(1.5, len(rows) * 0.8 + 1.2)))
    ax.axis("off")
    table = ax.table(
        cellText=rows,
        colLabels=columns,
        cellLoc="center",
        loc="center",
    )
    table.auto_set_font_size(False)
    table.set_fontsize(11)
    table.scale(1, 2.2)

    for (row_idx, col_idx), cell in table.get_celld().items():
        if row_idx == 0:
            cell.set_facecolor("#1f77b4")
            cell.set_text_props(color="white", fontweight="bold")
        elif row_idx % 2 == 0:
            cell.set_facecolor("#f0f4ff")
        cell.set_edgecolor("#cccccc")

    ax.set_title("Aggregate Segmentation Metrics — ATLAS Dataset 501",
                 fontsize=13, fontweight="bold", pad=12)

    out = out_dir / "summary_table.png"
    fig.savefig(out)
    plt.close(fig)
    print(f"[OK] {out}")


# ── main ─────────────────────────────────────────────────────────────────────

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--results-dir", type=Path, required=False,
                        help="nnU-Net trainer results dir (contains fold_0/, fold_1/, ...)")
    parser.add_argument("--eval-json", type=Path, default=None,
                        help="Aggregate eval JSON from evaluate_segmentation.py")
    parser.add_argument("--eval-csv", type=Path, default=None,
                        help="Per-case CSV from evaluate_segmentation.py")
    parser.add_argument("--baseline-json", type=Path, default=None,
                        help="Aggregate eval JSON for heuristic baseline (optional)")
    parser.add_argument("--out-dir", type=Path, default=Path("docs/figures"))
    args = parser.parse_args()

    args.out_dir.mkdir(parents=True, exist_ok=True)

    if args.results_dir:
        plot_loss_curves(args.results_dir, args.out_dir)
        plot_dice_distribution(args.results_dir, args.out_dir)
    else:
        print("[INFO] --results-dir not provided, skipping loss/dice plots")

    plot_metrics_comparison(args.eval_json, args.baseline_json, args.out_dir)
    plot_per_case_scatter(args.eval_csv, args.out_dir)
    plot_summary_table(args.eval_json, args.baseline_json, args.out_dir)

    print(f"\nAll figures saved to: {args.out_dir.resolve()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
