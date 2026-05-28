from app.postprocessing.report_builder import ReportBuildInput, build_report
from app.schemas.common import ExecutionMode, Modality


def test_build_report_marks_mri_heuristic_path_honestly():
    report_text, report_data = build_report(
        ReportBuildInput(
            modality=Modality.MRI,
            execution_mode=ExecutionMode.REAL,
            lesion_count=1,
            liver_model=False,
            lesion_model=False,
            supports_3d_liver=True,
            supports_3d_lesion=True,
        )
    )

    assert report_data.sections.findings == (
        "Structured output contains 1 heuristic suspicious-zone component(s) derived from the lesion mask."
    )
    assert report_data.sections.impression == (
        "1 heuristic suspicious-zone component(s) were derived from pipeline output and require clinical correlation."
    )
    assert report_data.sections.limitations == (
        "MRI suspicious-zone output is heuristic-supported in the current pipeline. "
        "Liver segmentation was produced without a dedicated liver model. "
        "All outputs remain decision-support only and depend on segmentation quality."
    )
    assert "heuristic suspicious-zone component" in report_text
