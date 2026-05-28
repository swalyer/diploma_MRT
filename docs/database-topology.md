# Database Topology

## Roles

1. **postgres-primary**
   - Source of truth for core domain data (users/cases/artifacts/findings/reports/inference runs).
   - Backend write target via `SPRING_DATASOURCE_URL`.

2. **postgres-replica**
   - Streaming replica of primary.
   - Intended for read-heavy/reporting/failover demonstration.
   - Exposed to backend as `APP_READ_DATASOURCE_URL` (routing scaffolded; writes stay on primary).

3. **audit-postgres**
   - Dedicated database for audit/security/pipeline events.
   - Backend writes audit events through separate JDBC datasource when `APP_AUDIT_ENABLED=true`.

## Replication/sync strategy

- Physical streaming replication: `postgres-primary -> postgres-replica` using `pg_basebackup` bootstrap + WAL sender config.
- Audit DB is isolated and **not** a replica; it receives explicit audit events from backend service logic.

## Backend read/write routing

- **Implemented now:**
  - Primary datasource for core JPA entities.
  - Separate audit datasource (`app.audit.datasource.*`) for audit writes/reads in `AuditServiceImpl`.
- **Prepared/scaffolded:**
  - Read replica connection envs (`APP_READ_REPLICA_ENABLED`, `APP_DB_REPLICA_*`, `APP_READ_DATASOURCE_*`) included for future query routing.

## Real vs simulated

- Streaming replica setup is real at container level.
- Automatic failover/orchestration is not implemented (no Patroni/Stolon/repmgr).
- Read routing to replica is documented/scaffolded but not fully switched at repository layer.
