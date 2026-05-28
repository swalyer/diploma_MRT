# MRI Production Roadmap

Цель: довести MRI-ветку до production-grade состояния — **обученная модель сегментации печёночных лезий + интерактивный refinement через MedSAM + честный rule-based baseline**. Без полу-меры и эвристик-под-видом-моделей.

## Ограничения и допущения

- Один разработчик, дипломный проект.
- Реальных пациентских данных нет — обучаемся на публичных open-access датасетах.
- GPU: предполагается доступ к 8GB+ (Colab Pro / Kaggle / уни-кластер). Без GPU трек B нереалистичен.
- Multi-phase MRI пока не поддерживается pipeline-ом (вход — один volume). Расширим, если выберем LLD-MMRI; для ATLAS достаточно single-phase post-contrast T1.

## Целевая архитектура

```
MRI volume (NIfTI)
   ↓
[Preprocessing]   N4 bias correction → resample 1×1×1мм → z-score normalize
   ↓
[Liver segmentation]   TotalSegmentator task=total_mr  (already works)
   ↓
[Lesion segmentation]   nnU-Net v2 (trained on ATLAS)   ← основная модель
   ↓                        OR
   ↓   (fallback / refinement path)
   ↓   MedSAM-prompted from heuristic candidate bboxes  ← интерактивный путь
   ↓                        OR
   ↓   z-score heuristic                                ← honest baseline
   ↓
[Postprocessing]   connected components → mesh GLB → findings + confidence
   ↓
Backend → Frontend (2D overlay + 3D mesh + interactive bbox refine)
```

## Датасеты

**Основной — ATLAS (Quinton et al., 2023):** ~90 train + 60 hidden test, single-phase post-contrast T1 MRI, manual liver tumor masks, CC BY 4.0. Подходит для обучения и валидации.

**Дополнительный — CHAOS T1/T2:** 40 cases, organ-only (печень без опухолей), open. Используем для pre-train liver mask на MRI (если TotalSegmentator MRI окажется недостаточно точным).

**Опционально — LLD-MMRI:** multi-phase MRI с lesion bbox/classification. Подключаем, если дойдём до фазового pipeline. На MVP не критично.

## Треки реализации

### Трек A — Honest Heuristic Baseline (фундамент)
Цель: дать честную точку отсчёта. Текущая `save_heuristic_lesion_mask` — это и есть baseline, нужно её аккуратно валидировать.

- Прогнать эвристику на ATLAS test split.
- Метрики: Dice, sensitivity per-lesion, FP/case, HD95.
- Эвристика должна возвращать честный `confidence` (не None) — функция от z-score amplitude и размера компоненты.
- Сохранить как `docs/baseline-metrics.md`.

### Трек B — nnU-Net на ATLAS (главный)
Цель: настоящая обученная модель.

- nnU-Net v2 auto-конфигурация на ATLAS train.
- 5-fold CV, 3d_fullres plan (или 2d, если GPU не вытягивает).
- Ожидаемые метрики (литература на ATLAS): Dice 0.55–0.70 на лезиях, 0.92+ на печени.
- Веса хранить вне репо (S3 / HuggingFace Hub), в репо — manifest с URL+SHA256.
- `NnUnetAdapter` уже умеет вызывать `nnUNetv2_predict` — нужно подменить датасет на `Dataset501_AtlasMRILesion`.
- В UI убрать чип «honest-ready · heuristic-supported», когда `lesion_real=true`.

### Трек C — MedSAM Interactive Refinement (UX-фишка)
Цель: радиолог-в-петле, ML-grade границы без обучения.

- Скачать `medsam_vit_b.pth` (~358MB, MIT license).
- `MedSamAdapter` переписать: per-slice promptable inference от bbox → 3D merge.
- Hybrid path: эвристика/nnU-Net выдают candidate bbox-ы → MedSAM уточняет границы.
- Новый endpoint `POST /api/cases/{id}/segment/interactive` с bbox-prompt.
- Frontend: в `Medical2DViewer` кнопка «Refine selection» — пользователь рисует bbox, MedSAM сегментирует, маска перезаписывается.

## Спринты

### Спринт 1: Preprocessing + dataset prep (3–5 дней)
- [ ] `ml-service/app/preprocessing/mri_preprocessing.py`: N4 bias correction + isotropic resample + z-score normalize.
- [ ] Включить MRI preprocessing в `real_pipeline.py` перед liver segmentation.
- [ ] `ml-service/scripts/prepare_atlas_dataset.py`: ATLAS → nnU-Net v2 layout (`imagesTr/`, `labelsTr/`, `dataset.json` с `Dataset501_AtlasMRILesion`).
- [ ] Smoke-тест: один кейс ATLAS прогнать через pipeline, проверить что не падает.

### Спринт 2: Honest baseline (2–3 дня)
- [ ] `ml-service/scripts/evaluate_segmentation.py`: универсальный evaluator (Dice/sensitivity/HD95/FP-rate) для любого предсказания vs ground truth.
- [ ] Прогон эвристики на ATLAS test, фиксация метрик.
- [ ] Добавить `confidence` в `save_heuristic_lesion_mask` (z-score amplitude × log(size) → clip [0,1]).
- [ ] `docs/baseline-metrics.md` с таблицей метрик.

### Спринт 3: nnU-Net training (1–2 недели)
- [ ] nnU-Net v2 install, `nnUNetv2_plan_and_preprocess -d 501`.
- [ ] `nnUNetv2_train 501 3d_fullres 0..4` (5 fold).
- [ ] `nnUNetv2_find_best_configuration 501`.
- [ ] Веса → object storage / HuggingFace.
- [ ] `ml-service/app/weights_manifest.yml`: модель → URL + SHA256 + версия.
- [ ] `NnUnetAdapter` подключить к `Dataset501_AtlasMRILesion`.
- [ ] Прогон evaluator на test split, сравнение с baseline.

### Спринт 4: MedSAM integration (3–5 дней)
- [ ] Скачать MedSAM weights, добавить в weights_manifest.
- [ ] `MedSamAdapter.segment_lesion` — per-slice inference, 3D merge.
- [ ] Hybrid mode: bbox от nnU-Net (или от эвристики) → MedSAM refine.
- [ ] Backend endpoint `POST /api/cases/{id}/segment/interactive`.
- [ ] Frontend bbox drawing tool в `Medical2DViewer`, проброс в новый endpoint.

### Спринт 5: Production hardening (3–5 дней)
- [ ] `docs/model-card-mri-lesion.md`: train data, metrics, intended use, known limitations, fairness notes.
- [ ] Детерминированный smoke-test: known ATLAS case → Dice >= threshold.
- [ ] Сравнительная таблица heuristic / nnU-Net / MedSAM-refined.
- [ ] Графики: Dice distribution, FROC curve, lesion size vs Dice.
- [ ] CI: проверка веса-manifest целостности (SHA256), smoke на mini-кейсе.
- [ ] Пометить в UI execution mode честно: `MRI · nnU-Net v2 · trained on ATLAS`.

## Метрики качества (acceptance)

| Метрика | Heuristic baseline (ожидание) | nnU-Net target | nnU-Net + MedSAM refine |
|---|---|---|---|
| Dice (lesion) | 0.15–0.30 | 0.55–0.70 | +0.05–0.10 boundary improvement |
| Sensitivity per-lesion | 0.40–0.60 | 0.75+ | same as nnU-Net |
| FP per case | 3–8 | <2 | <1 (interactive) |
| Liver Dice | 0.85–0.92 (TotalSegmentator) | 0.92+ | n/a |

## Open questions

1. **GPU.** Какой доступ есть и на сколько времени? От этого зависит, идём в полный B или режем до 2d-plan / только inference.
2. **ATLAS access.** Зарегистрирован ли аккаунт на портале датасета? Скачивание ~10–20GB.
3. **Inference latency target.** Какой ожидается на запрос? nnU-Net 3d_fullres ~2–5 мин на CPU, ~30–60с на GPU. 2d_plan быстрее, но Dice ниже.
4. **Storage для весов.** S3 / HuggingFace / локальный bind-mount?
5. **Multi-phase.** Включаем LLD-MMRI или ограничиваемся single-phase ATLAS на MVP?

## Что считаем «production-ready»

- Обученная модель с воспроизводимыми метриками на public test set.
- Честный model card.
- Pipeline preprocessing (N4 + resample + normalize) до сегментации.
- Веса вне репо, manifest с проверкой целостности.
- Interactive refinement доступен из UI.
- В UI явно показано: какая модель отрабатывает, какие метрики у неё на test, в каком режиме (auto/interactive/heuristic).
- Smoke-тест валидирует Dice на известном кейсе.
- Honest fallback: если веса недоступны — эвристика, в UI чип `heuristic-supported`.
