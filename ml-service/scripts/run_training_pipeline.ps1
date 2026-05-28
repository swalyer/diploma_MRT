# ─────────────────────────────────────────────────────────────────────────────
# run_training_pipeline.ps1
# Full nnU-Net training pipeline for ATLAS Dataset 501 on Windows.
#
# Prerequisites:
#   1. ml-service\.venv with torch + nnunetv2 installed (see README).
#   2. ATLAS dataset extracted to $AtlasRoot.
#   3. ~50 GB free disk space.
#
# Usage:
#   .\scripts\run_training_pipeline.ps1 `
#       -AtlasRoot  C:\datasets\atlas_raw `
#       -FoldsToRun 0        # or "0,1,2,3,4" for all folds
#       -SkipSteps  ""       # comma-sep: "convert,preprocess,train,eval"
# ─────────────────────────────────────────────────────────────────────────────

param(
    [string]$AtlasRoot      = "$HOME\datasets\atlas_raw",
    [string]$FoldsToRun     = "0",
    [string]$SkipSteps      = "",
    [int]   $DatasetId      = 501,
    [string]$NnConfig       = "3d_fullres"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ── paths ─────────────────────────────────────────────────────────────────────
$RepoRoot   = Split-Path $PSScriptRoot -Parent
$Venv       = Join-Path $RepoRoot ".venv\Scripts"
$Python     = Join-Path $Venv "python.exe"
$NnRaw      = "$HOME\nnunet\raw"
$NnPrep     = "$HOME\nnunet\preprocessed"
$NnResults  = "$HOME\nnunet\results"
$WeightsDir = "$HOME\.cache\diploma_mrt_weights"
$DocsDir    = Join-Path $RepoRoot "..\docs\figures"
$EvalJson   = Join-Path $RepoRoot "..\docs\eval_atlas_fold0.json"
$EvalCsv    = Join-Path $RepoRoot "..\docs\eval_atlas_fold0_per_case.csv"
$BaseJson   = Join-Path $RepoRoot "..\docs\eval_heuristic_baseline.json"

# ── env vars for nnU-Net ──────────────────────────────────────────────────────
$env:nnUNet_raw           = $NnRaw
$env:nnUNet_preprocessed  = $NnPrep
$env:nnUNet_results       = $NnResults

# Persist for future sessions
[System.Environment]::SetEnvironmentVariable("nnUNet_raw",          $NnRaw,     "User")
[System.Environment]::SetEnvironmentVariable("nnUNet_preprocessed", $NnPrep,    "User")
[System.Environment]::SetEnvironmentVariable("nnUNet_results",      $NnResults, "User")
[System.Environment]::SetEnvironmentVariable("DIPLOMA_WEIGHTS_DIR", $WeightsDir,"User")

foreach ($dir in @($NnRaw, $NnPrep, $NnResults, $WeightsDir, $DocsDir)) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
}

$Skip = $SkipSteps -split "," | ForEach-Object { $_.Trim().ToLower() }

function Step-Header([string]$msg) {
    Write-Host "`n$('─' * 70)" -ForegroundColor Cyan
    Write-Host "  $msg" -ForegroundColor Cyan
    Write-Host "$('─' * 70)`n" -ForegroundColor Cyan
}

# ─── STEP 1: Convert ATLAS → nnU-Net format ───────────────────────────────────
if ("convert" -notin $Skip) {
    Step-Header "STEP 1/5 — Converting ATLAS dataset to nnU-Net format"
    & $Python (Join-Path $PSScriptRoot "prepare_atlas_dataset.py") `
        --atlas-root $AtlasRoot `
        --out-root   $NnRaw `
        --dataset-id $DatasetId
    if ($LASTEXITCODE -ne 0) { throw "Dataset conversion failed." }
} else { Write-Host "[SKIP] convert" }

# ─── STEP 2: Plan & preprocess ────────────────────────────────────────────────
if ("preprocess" -notin $Skip) {
    Step-Header "STEP 2/5 — nnUNetv2_plan_and_preprocess (10–20 min)"
    & (Join-Path $Venv "nnUNetv2_plan_and_preprocess.exe") `
        -d $DatasetId --verify_dataset_integrity
    if ($LASTEXITCODE -ne 0) { throw "Preprocessing failed." }
} else { Write-Host "[SKIP] preprocess" }

# ─── STEP 3: Train folds ──────────────────────────────────────────────────────
if ("train" -notin $Skip) {
    $folds = $FoldsToRun -split "," | ForEach-Object { [int]$_.Trim() }
    $total = $folds.Count
    $i = 1
    foreach ($fold in $folds) {
        Step-Header "STEP 3/5 — Training fold $fold / $($folds[-1]) ($i of $total)"
        Write-Host "  Expected time: ~4–6 h per fold on RTX 4070 Ti Super" -ForegroundColor Yellow
        & (Join-Path $Venv "nnUNetv2_train.exe") $DatasetId $NnConfig $fold
        if ($LASTEXITCODE -ne 0) { throw "Training fold $fold failed." }
        $i++
    }
} else { Write-Host "[SKIP] train" }

# ─── STEP 4: Find best config & export weights ────────────────────────────────
if ("export" -notin $Skip) {
    Step-Header "STEP 4/5 — Finding best config and exporting weights"
    $trainedFolds = ($FoldsToRun -split ",") -join " "
    & (Join-Path $Venv "nnUNetv2_find_best_configuration.exe") $DatasetId -c $NnConfig
    if ($LASTEXITCODE -ne 0) { Write-Warning "find_best_configuration returned non-zero (may be OK with single fold)" }

    $zipPath = Join-Path $WeightsDir "nnunet_atlas_mri_lesion_v0.1.0.zip"
    & (Join-Path $Venv "nnUNetv2_export_model_to_zip.exe") `
        -d $DatasetId -c $NnConfig -o $zipPath
    if ($LASTEXITCODE -ne 0) { throw "Model export failed." }

    $hash = (Get-FileHash -Algorithm SHA256 $zipPath).Hash.ToLower()
    Write-Host "`n  SHA256: $hash" -ForegroundColor Green
    Write-Host "  ← Copy this into ml-service/config/weights_manifest.yml" -ForegroundColor Yellow

    # Auto-patch weights_manifest.yml if it exists
    $manifestPath = Join-Path $RepoRoot "config\weights_manifest.yml"
    if (Test-Path $manifestPath) {
        (Get-Content $manifestPath) -replace "REPLACE_WITH_REAL_SHA256_AFTER_TRAINING", $hash |
            Set-Content $manifestPath -Encoding utf8
        Write-Host "  [OK] weights_manifest.yml patched with real SHA256" -ForegroundColor Green
    }
} else { Write-Host "[SKIP] export" }

# ─── STEP 5: Evaluate fold 0 predictions & generate figures ───────────────────
if ("eval" -notin $Skip) {
    Step-Header "STEP 5/5 — Evaluation + diploma figures"

    $DatasetDir = Join-Path $NnRaw "Dataset${DatasetId}_AtlasMRILesion"
    $Fold0 = 0
    $TrainerDir = Get-ChildItem (Join-Path $NnResults "Dataset${DatasetId}_AtlasMRILesion") |
                  Where-Object { $_.Name -like "nnUNetTrainer*" } |
                  Select-Object -First 1

    if ($TrainerDir) {
        $PredDir = Join-Path $TrainerDir.FullName "fold_${Fold0}\validation"

        # Evaluate fold 0 validation predictions against labelsTr
        & $Python (Join-Path $PSScriptRoot "evaluate_segmentation.py") `
            --dataset-root    $DatasetDir `
            --split           Tr `
            --predictor       predictions-dir `
            --predictions-dir $PredDir `
            --output          $EvalJson `
            --csv             $EvalCsv
        if ($LASTEXITCODE -ne 0) { Write-Warning "Evaluation returned non-zero — check paths" }

        # Heuristic baseline on the same split (for comparison chart)
        & $Python (Join-Path $PSScriptRoot "evaluate_segmentation.py") `
            --dataset-root $DatasetDir `
            --split        Tr `
            --predictor    heuristic `
            --output       $BaseJson `
            --csv          (Join-Path $RepoRoot "..\docs\eval_heuristic_baseline_per_case.csv")

        # Generate all diploma figures
        & $Python (Join-Path $PSScriptRoot "plot_training_results.py") `
            --results-dir  (Join-Path $TrainerDir.FullName "${NnConfig}") `
            --eval-json    $EvalJson `
            --eval-csv     $EvalCsv `
            --baseline-json $BaseJson `
            --out-dir      $DocsDir
    } else {
        Write-Warning "Could not find trainer results dir in $NnResults — skipping eval"
    }
} else { Write-Host "[SKIP] eval" }

Write-Host "`n$('═' * 70)" -ForegroundColor Green
Write-Host "  PIPELINE COMPLETE" -ForegroundColor Green
Write-Host "  Weights : $WeightsDir\nnunet_atlas_mri_lesion_v0.1.0.zip" -ForegroundColor Green
Write-Host "  Figures : $DocsDir" -ForegroundColor Green
Write-Host "$('═' * 70)`n" -ForegroundColor Green
