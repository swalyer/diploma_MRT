# Ports and Traffic Flows

## Port matrix

| Service | Port | Proto | Exposure | Notes |
|---|---:|---|---|---|
| reverse-proxy | 80, 443 | HTTP/HTTPS | Public | Single ingress |
| frontend | 80 | HTTP | Internal (`edge_net`) | Behind reverse proxy |
| backend | 8080 | HTTP | Internal | `/api` via reverse proxy |
| ml-service | 8000 | HTTP | Internal | backend-only path |
| postgres-primary | 5432 | TCP | Internal/data host | app writes + replication source |
| postgres-replica | 5433->5432 | TCP | Internal/data host | read-only target |
| audit-postgres | 5434->5432 | TCP | Internal/data host | audit events only |
| minio | 9000 | HTTP/S3 | Internal preferred | object store API |
| minio-console | 9001 | HTTP | Restricted admin | operator only |
| prometheus | 9090 | HTTP | Internal/admin | optional host 4 |
| grafana | 3000 | HTTP | Internal/admin | optional host 4 |

## Directional flows

- Client -> reverse-proxy (`80/443`)
- reverse-proxy -> frontend (`edge_net`)
- reverse-proxy -> backend `/api` (`app_net`)
- backend -> ml-service (`app_net`)
- backend -> postgres-primary (`data_net`)
- backend -> audit-postgres (`data_net`)
- backend -> postgres-replica (`data_net`, read use-case)
- postgres-replica -> postgres-primary (`replication_net`, WAL/replication)
- backend/ml-service -> minio (`data_net`)
- prometheus -> backend/ml/db exporters (`obs_net`)

## Firewall / security group recommendations

- Internet-facing: allow only `80/443` to host 1.
- Host 2: allow inbound from host 1 to backend port (or private overlay only).
- Host 3: allow DB ports only from host 2 and host 3 replication peers.
- Host 4: restrict Grafana/Prometheus to VPN/admin CIDRs.
