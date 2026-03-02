# Deployment Topology

## Local demo (single-host)

Use root `docker-compose.yml` for defense/demo:

```bash
docker compose --env-file .env.example up --build -d
```

This runs all services on one machine but still keeps segmented Docker networks (`edge_net`, `app_net`, `data_net`, `replication_net`, `obs_net`) to preserve architecture semantics.

## Multi-host deployment (intended)

Split by host role:

- Host 1: `docker-compose.edge.yml`
- Host 2: `docker-compose.app.yml`
- Host 3: `docker-compose.data.yml`
- Host 4: `docker-compose.observability.yml` (optional)

### Inter-host connectivity assumptions

- Private routed network/VPN between hosts (WireGuard/Tailscale/L2 VPN/LAN).
- DNS or `/etc/hosts` entries for cross-host names (`edge.internal`, `app.internal`, `data.internal`, `obs.internal`).
- Firewall allows only required east-west ports.

### Startup order

1. Host 3 (data): DBs + MinIO.
2. Host 2 (application): backend + ml-service.
3. Host 1 (edge): reverse proxy + frontend.
4. Host 4 (observability).

### Shared networks in split compose mode

In split-compose, `app_net`, `data_net`, `obs_net` are represented as cross-host private connectivity domains (implemented by VPN/LAN rather than single Docker bridge across hosts).

### Required env (baseline)

- Shared: `APP_JWT_SECRET`, `APP_ML_MODE`, `ML_MODE`.
- Public/API: `APP_PUBLIC_BASE_URL`, `VITE_API_BASE_URL`.
- Primary DB: `APP_DB_PRIMARY_*` + `SPRING_DATASOURCE_*`.
- Replica DB: `APP_DB_REPLICA_*`, `APP_READ_REPLICA_ENABLED`, `APP_READ_DATASOURCE_*`.
- Audit DB: `APP_AUDIT_ENABLED`, `APP_AUDIT_DB_*`, `APP_AUDIT_DATASOURCE_*`.
- Storage: `APP_STORAGE_ENDPOINT`, `APP_STORAGE_ACCESS_KEY`, `APP_STORAGE_SECRET_KEY`, `APP_STORAGE_BUCKET`, `APP_STORAGE_ROOT`.
- Observability/runtime: `APP_OBSERVABILITY_ENABLED`, `APP_LOG_LEVEL`.

Use `.env.example` as the canonical variable reference.

## Capability and limitation checklist

- ✅ Implemented: segmented networks and host-role deployment files.
- ✅ Implemented: reverse-proxy ingress with frontend + `/api` backend routing.
- ✅ Implemented: primary/replica/audit DB split and replication bootstrap scaffolding.
- ✅ Implemented: backend audit datasource routing (`APP_AUDIT_ENABLED=true`).
- ✅ Implemented: explicit env model for multi-host DNS/IP deployment.
- ⚠️ Partial: backend read traffic routing to replica is prepared via envs but not repository-level switched.
- ⚠️ Partial: MinIO is networked and configured by env, but backend continues using filesystem-root flow by default.
- ❌ Not implemented: automated failover/promotion and production HA orchestration.
- ❌ Not implemented: mTLS / service mesh / fine-grained network policy engine.
