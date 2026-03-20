#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path
from typing import Any
from urllib import error, parse, request


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_MANIFEST = ROOT / "demo-data" / "manifests" / "ct-single-lesion-001.json"


def main() -> int:
    args = parse_args()
    manifest = load_json(args.manifest)

    print_step(f"Health check {args.base_url}/actuator/health")
    health = request_json(args.base_url, "GET", "/actuator/health", timeout=args.timeout)
    expect_equal(health.get("status"), "UP", "backend health status")

    print_step("Login as admin")
    admin_token = login(args.base_url, args.admin_email, args.admin_password, args.timeout)
    print_step(f"Import seeded manifest {manifest['caseSlug']}")
    imported = request_json(
        args.base_url,
        "POST",
        "/api/admin/demo-cases/import",
        token=admin_token,
        json_body=manifest,
        timeout=args.timeout,
    )
    expect_in(imported.get("action"), {"CREATED", "UPDATED"}, "import action")
    expect_equal(imported.get("caseSlug"), manifest["caseSlug"], "import caseSlug")
    expect_equal(imported.get("schemaVersion"), manifest["schemaVersion"], "import schemaVersion")
    expect_equal(imported.get("origin"), manifest["origin"], "import origin")
    expect_equal(imported.get("artifactCount"), len(manifest["artifacts"]), "import artifactCount")
    expect_equal(imported.get("findingCount"), len(manifest["findings"]), "import findingCount")
    expect_equal(imported.get("report", {}).get("reportData"), manifest["reportData"], "import reportData")
    expect_equal(imported.get("report", {}).get("reportText"), manifest["reportText"], "import reportText")
    case_id = imported.get("caseId")
    expect_true(isinstance(case_id, int), "import caseId is integer")

    print_step(f"Validate seeded case read model for case {case_id}")
    case_payload = request_json(args.base_url, "GET", f"/api/cases/{case_id}", token=admin_token, timeout=args.timeout)
    expect_equal(case_payload.get("id"), case_id, "case id")
    expect_equal(case_payload.get("status"), "COMPLETED", "case status")
    expect_equal(case_payload.get("origin"), manifest["origin"], "case origin")
    expect_equal(case_payload.get("modality"), manifest["modality"], "case modality")
    expect_equal(case_payload.get("patientPseudoId"), manifest["patientPseudoId"], "case patientPseudoId")
    expect_equal(case_payload.get("demoCategory"), manifest["category"], "case demoCategory")
    expect_equal(case_payload.get("demoCaseSlug"), manifest["caseSlug"], "case demoCaseSlug")
    expect_equal(case_payload.get("demoManifestVersion"), manifest["schemaVersion"], "case demoManifestVersion")
    expect_equal(case_payload.get("sourceDataset"), manifest.get("sourceDataset"), "case sourceDataset")
    expect_equal(case_payload.get("sourceAttribution"), manifest.get("sourceAttribution"), "case sourceAttribution")
    expect_true(case_payload.get("inferenceStatus") is None, "seeded case inferenceStatus is null")
    expect_true(case_payload.get("executionMode") is None, "seeded case executionMode is null")

    status_payload = request_json(
        args.base_url,
        "GET",
        f"/api/cases/{case_id}/status",
        token=admin_token,
        timeout=args.timeout,
    )
    expect_equal(status_payload.get("caseId"), case_id, "status caseId")
    expect_equal(status_payload.get("status"), "COMPLETED", "status state")
    expect_true(status_payload.get("inferenceStatus") is None, "status inferenceStatus is null")
    expect_true(status_payload.get("executionMode") is None, "status executionMode is null")
    expect_true(status_payload.get("modelVersion") is None, "status modelVersion is null")
    expect_true(status_payload.get("metrics") is None, "status metrics is null")
    expect_true(status_payload.get("failureDetails") is None, "status failureDetails is null")
    expect_true(status_payload.get("resultReady") is True, "seeded resultReady is true")
    expect_equal(status_payload.get("resultSource"), "SEEDED_IMPORT", "seeded resultSource")
    audit_actions = [entry.get("action") for entry in status_payload.get("stageAuditTrail", [])]
    expect_true(bool(audit_actions), "stageAuditTrail is not empty")
    expect_true(imported["action"] == "CREATED" and "DEMO_CASE_IMPORTED" in audit_actions
                or imported["action"] == "UPDATED" and "DEMO_CASE_UPDATED" in audit_actions,
                "stageAuditTrail contains matching import audit action")

    print_step("Validate report/findings/artifacts/viewer payloads")
    report_payload = request_json(
        args.base_url,
        "GET",
        f"/api/cases/{case_id}/report",
        token=admin_token,
        timeout=args.timeout,
    )
    expect_equal(report_payload.get("reportText"), manifest["reportText"], "reportText")
    report_data = report_payload.get("reportData", {})
    expect_equal(report_data.get("modality"), manifest["modality"], "reportData modality")
    expect_true(report_data.get("executionMode") is None, "reportData executionMode is null")
    expect_equal(report_data.get("lesionCount"), len(manifest["findings"]), "reportData lesionCount")
    expect_true(report_data.get("evidenceBound") is True, "reportData evidenceBound")
    expect_equal(report_data.get("sections"), manifest["reportData"], "reportData sections")
    expected_caps = {
        "supports3dLiver": True,
        "supports3dLesion": any(artifact["type"] == "LESION_MESH" for artifact in manifest["artifacts"]),
    }
    expect_equal(report_data.get("capabilities"), expected_caps, "reportData capabilities")

    findings_payload = request_json(
        args.base_url,
        "GET",
        f"/api/cases/{case_id}/findings",
        token=admin_token,
        timeout=args.timeout,
    )
    expect_equal(len(findings_payload), len(manifest["findings"]), "findings count")
    expect_equal(
        normalize_findings(findings_payload),
        normalize_findings(manifest["findings"]),
        "findings payload matches manifest",
    )

    artifacts_payload = request_json(
        args.base_url,
        "GET",
        f"/api/cases/{case_id}/artifacts",
        token=admin_token,
        timeout=args.timeout,
    )
    expect_equal(len(artifacts_payload), len(manifest["artifacts"]), "artifact count")
    artifacts_by_type = map_artifacts_by_type(artifacts_payload)
    manifest_by_type = {artifact["type"]: artifact for artifact in manifest["artifacts"]}
    expect_equal(set(artifacts_by_type), set(manifest_by_type), "artifact types")
    for artifact_type, api_artifact in artifacts_by_type.items():
        manifest_artifact = manifest_by_type[artifact_type]
        expect_equal(api_artifact.get("fileName"), manifest_artifact["fileName"], f"{artifact_type} fileName")
        expect_equal(api_artifact.get("mimeType"), manifest_artifact["mimeType"], f"{artifact_type} mimeType")
        expect_true(api_artifact.get("downloadUrl", "").startswith("/api/files/"), f"{artifact_type} downloadUrl")

    viewer_payload = request_json(
        args.base_url,
        "GET",
        f"/api/cases/{case_id}/viewer/3d",
        token=admin_token,
        timeout=args.timeout,
    )
    expect_equal(
        viewer_payload.get("liverMeshArtifactId"),
        artifacts_by_type["LIVER_MESH"]["id"],
        "viewer liver mesh artifact id",
    )
    expected_lesion_mesh = artifacts_by_type.get("LESION_MESH", {}).get("id")
    expect_equal(viewer_payload.get("lesionMeshArtifactId"), expected_lesion_mesh, "viewer lesion mesh artifact id")

    print_step("Verify admin download access and binary integrity")
    for artifact_type, api_artifact in artifacts_by_type.items():
        manifest_artifact = manifest_by_type[artifact_type]
        headers, payload = request_bytes(
            args.base_url,
            "GET",
            api_artifact["downloadUrl"],
            token=admin_token,
            timeout=args.timeout,
        )
        content_type = headers.get("Content-Type", headers.get("content-type"))
        disposition = headers.get("Content-Disposition", headers.get("content-disposition", ""))
        expect_equal(content_type, manifest_artifact["mimeType"], f"{artifact_type} download content-type")
        expect_true(manifest_artifact["fileName"] in disposition, f"{artifact_type} content-disposition filename")
        expect_equal(len(payload), manifest_artifact["sizeBytes"], f"{artifact_type} download size")
        expect_equal(hashlib.sha256(payload).hexdigest(), manifest_artifact["sha256"], f"{artifact_type} download sha256")

    print_step("Login as doctor and validate read-only seeded access")
    doctor_token = login(args.base_url, args.doctor_email, args.doctor_password, args.timeout)
    doctor_case = request_json(args.base_url, "GET", f"/api/cases/{case_id}", token=doctor_token, timeout=args.timeout)
    expect_equal(doctor_case.get("id"), case_id, "doctor can open seeded case")
    doctor_artifacts = request_json(
        args.base_url,
        "GET",
        f"/api/cases/{case_id}/artifacts",
        token=doctor_token,
        timeout=args.timeout,
    )
    expect_equal(len(doctor_artifacts), len(manifest["artifacts"]), "doctor artifact count")
    sample_download = artifacts_by_type["ORIGINAL_STUDY"]["downloadUrl"]
    _, sample_payload = request_bytes(args.base_url, "GET", sample_download, token=doctor_token, timeout=args.timeout)
    expect_equal(
        hashlib.sha256(sample_payload).hexdigest(),
        manifest_by_type["ORIGINAL_STUDY"]["sha256"],
        "doctor download sha256",
    )
    _, doctor_process_error = request_json_response(
        args.base_url,
        "POST",
        f"/api/cases/{case_id}/process",
        token=doctor_token,
        timeout=args.timeout,
        expected_status={403},
    )
    expect_true("error" in doctor_process_error, "doctor process returns error body")

    print_step("Validate admin seeded rerun semantics")
    _, admin_process_error = request_json_response(
        args.base_url,
        "POST",
        f"/api/cases/{case_id}/process",
        token=admin_token,
        timeout=args.timeout,
        expected_status={409},
    )
    expect_true(
        "seeded demo cases" in str(admin_process_error.get("error", "")).lower(),
        "admin seeded process is intentionally disabled",
    )

    print_step(f"Seeded API smoke passed for case {case_id} ({manifest['caseSlug']})")
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Smoke-test seeded case API/read/download flow against a live stack.")
    parser.add_argument(
        "--base-url",
        default="http://localhost",
        help="Public base URL for the running stack.",
    )
    parser.add_argument(
        "--manifest",
        type=Path,
        default=DEFAULT_MANIFEST,
        help="Path to the seeded demo manifest JSON.",
    )
    parser.add_argument("--admin-email", default="admin@demo.local", help="Admin email used for import.")
    parser.add_argument("--admin-password", default="Admin123!", help="Admin password used for import.")
    parser.add_argument("--doctor-email", default="doctor@demo.local", help="Doctor email used for read-only checks.")
    parser.add_argument("--doctor-password", default="Admin123!", help="Doctor password used for read-only checks.")
    parser.add_argument("--timeout", type=float, default=20.0, help="HTTP timeout in seconds.")
    return parser.parse_args()


def load_json(path: Path) -> dict[str, Any]:
    if not path.exists():
        raise SystemExit(f"Manifest not found: {path}")
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def login(base_url: str, email: str, password: str, timeout: float) -> str:
    payload = request_json(
        base_url,
        "POST",
        "/api/auth/login",
        json_body={"email": email, "password": password},
        timeout=timeout,
    )
    token = payload.get("token")
    expect_true(isinstance(token, str) and token, f"login token for {email}")
    return token


def request_json(
    base_url: str,
    method: str,
    path_or_url: str,
    *,
    token: str | None = None,
    json_body: dict[str, Any] | None = None,
    timeout: float,
) -> Any:
    _, payload = request_json_response(
        base_url,
        method,
        path_or_url,
        token=token,
        json_body=json_body,
        timeout=timeout,
        expected_status={200},
    )
    return payload


def request_json_response(
    base_url: str,
    method: str,
    path_or_url: str,
    *,
    token: str | None = None,
    json_body: dict[str, Any] | None = None,
    timeout: float,
    expected_status: set[int],
) -> tuple[dict[str, str], Any]:
    headers, body = request_bytes(
        base_url,
        method,
        path_or_url,
        token=token,
        json_body=json_body,
        timeout=timeout,
        expected_status=expected_status,
    )
    try:
        return headers, json.loads(body.decode("utf-8"))
    except json.JSONDecodeError as exc:
        raise AssertionError(f"Expected JSON response for {method} {path_or_url}: {body[:200]!r}") from exc


def request_bytes(
    base_url: str,
    method: str,
    path_or_url: str,
    *,
    token: str | None = None,
    json_body: dict[str, Any] | None = None,
    timeout: float,
    expected_status: set[int] | None = None,
) -> tuple[dict[str, str], bytes]:
    expected_status = expected_status or {200}
    url = absolute_url(base_url, path_or_url)
    payload = None if json_body is None else json.dumps(json_body).encode("utf-8")
    headers = {"Accept": "application/json"}
    if payload is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"

    req = request.Request(url, data=payload, headers=headers, method=method)
    try:
        with request.urlopen(req, timeout=timeout) as response:
            status = response.getcode()
            body = response.read()
            response_headers = dict(response.headers.items())
    except error.HTTPError as exc:
        status = exc.code
        body = exc.read()
        response_headers = dict(exc.headers.items())
    except error.URLError as exc:
        raise AssertionError(f"Request failed for {method} {url}: {exc}") from exc

    if status not in expected_status:
        raise AssertionError(
            f"Unexpected HTTP {status} for {method} {url}; expected {sorted(expected_status)}; body={body[:400]!r}"
        )
    return response_headers, body


def absolute_url(base_url: str, path_or_url: str) -> str:
    if path_or_url.startswith("http://") or path_or_url.startswith("https://"):
        return path_or_url
    return parse.urljoin(base_url.rstrip("/") + "/", path_or_url.lstrip("/"))


def map_artifacts_by_type(artifacts: list[dict[str, Any]]) -> dict[str, dict[str, Any]]:
    mapped: dict[str, dict[str, Any]] = {}
    for artifact in artifacts:
        artifact_type = artifact["type"]
        expect_true(artifact_type not in mapped, f"artifact type {artifact_type} is unique")
        mapped[artifact_type] = artifact
    return mapped


def normalize_findings(findings: list[dict[str, Any]]) -> list[dict[str, Any]]:
    normalized = []
    for finding in findings:
        normalized.append(
            {
                "type": finding["type"],
                "label": finding["label"],
                "confidence": finding.get("confidence"),
                "sizeMm": finding.get("sizeMm"),
                "volumeMm3": finding.get("volumeMm3"),
                "location": normalize_location(finding.get("location")),
            }
        )
    return sorted(normalized, key=lambda item: (item["type"], item["label"]))


def normalize_location(location: dict[str, Any] | None) -> dict[str, Any] | None:
    if location is None:
        return None
    bbox = location.get("bbox")
    return {
        "segment": location.get("segment"),
        "centroid": location.get("centroid"),
        "bbox": None if bbox is None else {"min": bbox.get("min"), "max": bbox.get("max")},
        "extent": location.get("extent"),
        "suspicion": location.get("suspicion"),
    }


def print_step(message: str) -> None:
    print(f"[smoke] {message}")


def expect_equal(actual: Any, expected: Any, label: str) -> None:
    if actual != expected:
        raise AssertionError(f"{label}: expected {expected!r}, got {actual!r}")


def expect_in(actual: Any, expected: set[Any], label: str) -> None:
    if actual not in expected:
        raise AssertionError(f"{label}: expected one of {sorted(expected)!r}, got {actual!r}")


def expect_true(value: Any, label: str) -> None:
    if not value:
        raise AssertionError(f"{label}: expected truthy value")


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as exc:
        print(f"[smoke] FAILED: {exc}", file=sys.stderr)
        raise SystemExit(1) from exc
