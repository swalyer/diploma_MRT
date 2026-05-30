"""Prometheus instrumentation for the ML service.

Exposes a ``/metrics`` endpoint and records request count + latency per
route/method/status, so Prometheus can scrape the same way it scrapes the
backend's actuator endpoint. Kept dependency-light (prometheus_client only).
"""
from __future__ import annotations

import time

from fastapi import FastAPI, Request, Response
from prometheus_client import CONTENT_TYPE_LATEST, Counter, Histogram, generate_latest

REQUESTS = Counter(
    "ml_http_requests_total",
    "Total ML-service HTTP requests",
    ["method", "path", "status"],
)
LATENCY = Histogram(
    "ml_http_request_duration_seconds",
    "ML-service HTTP request latency",
    ["method", "path"],
)


def setup_metrics(app: FastAPI) -> None:
    @app.middleware("http")
    async def _record(request: Request, call_next):
        if request.url.path == "/metrics":
            return await call_next(request)
        # Use the route template (not the raw path) to keep label cardinality bounded.
        route = request.scope.get("route")
        path = getattr(route, "path", request.url.path)
        start = time.perf_counter()
        status = 500
        try:
            response = await call_next(request)
            status = response.status_code
            return response
        finally:
            LATENCY.labels(request.method, path).observe(time.perf_counter() - start)
            REQUESTS.labels(request.method, path, str(status)).inc()

    @app.get("/metrics", include_in_schema=False)
    def metrics() -> Response:
        return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)
