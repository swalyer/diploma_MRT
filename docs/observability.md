# Observability

The stack exposes Prometheus-format metrics from both services and ships a
Grafana dashboard provisioned out of the box.

## Metric endpoints
| Service | Endpoint | Source |
| --- | --- | --- |
| Backend | `GET /actuator/prometheus` | Spring Boot Actuator + `micrometer-registry-prometheus` (tagged `application="mrt-backend"`) |
| ML service | `GET /metrics` | `prometheus_client` middleware in `app/observability.py` |

Both are unauthenticated scrape endpoints (the backend one is allowlisted in
`SecurityConfig`); they live on the internal `obs_net` segment.

Key series:
- Backend: `http_server_requests_seconds_{count,bucket}` (rate + latency by `uri`),
  `jvm_memory_used_bytes`, plus the standard JVM/process metrics.
- ML service: `ml_http_requests_total{method,path,status}`,
  `ml_http_request_duration_seconds_bucket`.

## Running Prometheus + Grafana
Prometheus and Grafana run from `docker-compose.observability.yml`. Bring them up
alongside the app stack so they share the `obs_net` network and can resolve the
`backend` / `ml-service` service names:

```bash
docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d
```

- Prometheus: http://localhost:9090 (scrape config: `deploy/observability/prometheus.yml`)
- Grafana: http://localhost:3000 (default `admin` / `admin`, override with
  `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD`)

Grafana auto-provisions:
- the Prometheus datasource (`deploy/observability/grafana/provisioning/datasources`)
- the **MRT — Service Overview** dashboard
  (`deploy/observability/grafana/dashboards/mrt-overview.json`): target health,
  backend request rate + p95 latency, JVM heap, ML-service request rate + p95.

## Verifying without Docker
The scrape endpoints can be checked directly against a locally running stack:

```bash
curl -s localhost:8080/actuator/prometheus | head
curl -s localhost:8000/metrics | head
```

`APP_OBSERVABILITY_ENABLED` remains the backend feature flag for environment
wiring; the Prometheus endpoint itself is always exported so a scraper can be
attached in any environment.
