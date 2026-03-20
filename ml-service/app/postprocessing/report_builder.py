from dataclasses import dataclass

from app.schemas.common import ExecutionMode, Modality
from app.schemas.infer_response import ReportCapabilities, ReportData, ReportSections


@dataclass(frozen=True)
class ReportBuildInput:
    modality: Modality
    execution_mode: ExecutionMode
    lesion_count: int
    liver_model: bool | None
    lesion_model: bool | None
    supports_3d_liver: bool
    supports_3d_lesion: bool


def build_report(report_input: ReportBuildInput) -> tuple[str, ReportData]:
    sections = _build_sections(report_input)
    text = "\n\n".join(
        [
            f"Findings: {sections.findings}",
            f"Impression: {sections.impression}",
            f"Limitations: {sections.limitations}",
            f"Recommendation: {sections.recommendation}",
        ]
    )
    payload = ReportData(
        modality=report_input.modality,
        executionMode=report_input.execution_mode,
        lesionCount=report_input.lesion_count,
        evidenceBound=report_input.execution_mode == ExecutionMode.REAL,
        sections=sections,
        capabilities=ReportCapabilities(
            supports3dLiver=report_input.supports_3d_liver,
            supports3dLesion=report_input.supports_3d_lesion,
        ),
    )
    return text, payload


def _build_sections(report_input: ReportBuildInput) -> ReportSections:
    if report_input.execution_mode == ExecutionMode.MOCK:
        lesion_line = (
            f"Synthetic lesion components generated: {report_input.lesion_count}."
            if report_input.lesion_count > 0
            else "Synthetic lesion components were not generated for this input."
        )
        return ReportSections(
            findings=f"Mock pipeline generated synthetic workflow artifacts. {lesion_line}",
            impression="Synthetic workflow output only; no clinical interpretation is produced.",
            limitations="Mock mode does not evaluate pathology and must not be interpreted as patient evidence.",
            recommendation="Use mock outputs only for integration, UI, and workflow verification.",
        )

    findings = (
        f"Structured output contains {report_input.lesion_count} lesion component(s) derived from the lesion mask."
        if report_input.lesion_count > 0
        else "Structured output contains no lesion components derived from the lesion mask."
    )
    impression = (
        f"{report_input.lesion_count} lesion component(s) were derived from pipeline output and require clinical correlation."
        if report_input.lesion_count > 0
        else "No lesion components were derived from pipeline output."
    )

    limitations_parts = []
    if report_input.modality == Modality.MRI and report_input.lesion_model is False:
        limitations_parts.append("MRI lesion output is heuristic-supported in the current pipeline.")
    elif report_input.lesion_model is False:
        limitations_parts.append("Lesion output was produced without a dedicated lesion model.")
    if report_input.liver_model is False:
        limitations_parts.append("Liver segmentation was produced without a dedicated liver model.")
    limitations_parts.append("All outputs remain decision-support only and depend on segmentation quality.")

    return ReportSections(
        findings=findings,
        impression=impression,
        limitations=" ".join(limitations_parts),
        recommendation="Correlate with source images and radiologist review before clinical use.",
    )
