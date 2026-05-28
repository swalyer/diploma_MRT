import json
from pathlib import Path
from typing import Any

from app.main import app


def _normalize(node: Any) -> Any:
    if isinstance(node, dict):
        allowed = {}
        for key, value in node.items():
            if key in {"title", "summary", "operationId", "description"}:
                continue
            allowed[key] = _normalize(value)
        return allowed
    if isinstance(node, list):
        return [_normalize(item) for item in node]
    return node


def _normalize_openapi(schema: dict[str, Any]) -> dict[str, Any]:
    normalized_paths: dict[str, Any] = {}
    for path, methods in schema["paths"].items():
        normalized_methods: dict[str, Any] = {}
        for method, operation in methods.items():
            normalized_operation: dict[str, Any] = {"responses": {}}
            request_body = operation.get("requestBody", {}).get("content", {}).get("application/json", {}).get("schema")
            if request_body is not None:
                normalized_operation["requestBody"] = _normalize(request_body)
            for status, response in operation.get("responses", {}).items():
                content = response.get("content", {}).get("application/json", {}).get("schema")
                normalized_operation["responses"][status] = {"application/json": _normalize(content)} if content is not None else {}
            normalized_methods[method] = normalized_operation
        normalized_paths[path] = normalized_methods
    return {
        "openapi": schema["openapi"],
        "info": {
            "title": schema["info"]["title"],
            "version": schema["info"]["version"],
        },
        "paths": normalized_paths,
        "components": {
            "schemas": {
                name: _normalize(component)
                for name, component in schema.get("components", {}).get("schemas", {}).items()
            }
        },
    }


def test_openapi_snapshot_matches_contract() -> None:
    snapshot_path = Path(__file__).resolve().parents[1] / "contracts" / "openapi.snapshot.json"
    expected = json.loads(snapshot_path.read_text(encoding="utf-8"))
    actual = _normalize_openapi(app.openapi())
    assert actual == expected
