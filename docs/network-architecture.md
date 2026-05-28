# Network Architecture (Distributed MVP)

## 1. Target host layout

- **Host 1 (edge/web):** `reverse-proxy`, `frontend`.
- **Host 2 (application):** `backend`, `ml-service`.
- **Host 3 (data):** `postgres-primary`, `postgres-replica`, `audit-postgres`, `minio`.
- **Host 4 (observability, optional):** `prometheus`, `grafana`.

## 2. Logical networks and intent

- `edge_net`: public ingress segment. Contains only edge-facing path (`reverse-proxy`, `frontend`, backend ingress).
- `app_net`: private app-to-app segment (`backend` <-> `ml-service`).
- `data_net`: private data access segment (`backend`, `ml-service`, databases, MinIO).
- `replication_net`: DB internal replication/sync segment (`postgres-primary`, `postgres-replica`, optional audit sync).
- `obs_net`: telemetry segment for metrics/log collection.

## 3. Boundary rules

- Frontend has no direct DB access.
- Public traffic enters only through reverse proxy (80/443).
- `ml-service` is internal only (no public host port in distributed topology).
- `postgres-replica` is read-only target for reporting/failover demos.
- `audit-postgres` is isolated as dedicated audit/event store.
- Replication path is separated via `replication_net`.

## 4. Service communication matrix

| Source | Destination | Network | Purpose |
|---|---|---|---|
| Browser client | reverse-proxy | public -> edge_net | Entry point |
| reverse-proxy | frontend | edge_net | UI delivery |
| reverse-proxy | backend | app_net | `/api` routing |
| backend | ml-service | app_net | inference requests |
| backend | postgres-primary | data_net | write path |
| backend | postgres-replica | data_net | read/report path (prepared) |
| backend | audit-postgres | data_net | audit writes |
| backend/ml-service | minio | data_net | object operations |
| postgres-replica | postgres-primary | replication_net | streaming replication |
| observability stack | app/data services | obs_net | scrape/collect telemetry |

## 5. Public vs private services

**Public:** reverse-proxy (80/443), frontend via reverse proxy.

**Private/internal:** backend, ml-service, all PostgreSQL instances, MinIO API (optionally private only), Prometheus/Grafana (recommend VPN/IP allow-list).

## 6. Diagram

See `docs/diagrams/network-segmentation.mmd`.
