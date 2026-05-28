#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export APP_JWT_SECRET="${APP_JWT_SECRET:-ci-jwt-secret-0123456789abcdef0123456789abcdef}"
export COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-mrt-fulltest}"
export FULLTEST_HTTP_PORT="${FULLTEST_HTTP_PORT:-8088}"
export PLAYWRIGHT_BASE_URL="${PLAYWRIGHT_BASE_URL:-http://localhost:${FULLTEST_HTTP_PORT}}"
PROXY_CONTAINER_NAME="${COMPOSE_PROJECT_NAME}-reverse-proxy-standalone"

cleanup() {
  docker rm -f "$PROXY_CONTAINER_NAME" >/dev/null 2>&1 || true
  (cd "$ROOT_DIR" && docker compose down -v --remove-orphans >/dev/null 2>&1) || true
}

trap cleanup EXIT

cd "$ROOT_DIR/backend"
mvn test -q

cd "$ROOT_DIR/frontend"
npm ci
npm run contract:check
npm run build

cd "$ROOT_DIR"
docker compose build ml-service-test
docker compose run --rm --no-deps ml-service-test pytest -q

cd "$ROOT_DIR/frontend/e2e"
npm install
npx playwright install --with-deps chromium

cd "$ROOT_DIR"
APP_ML_MODE=mock ML_MODE=mock docker compose up -d --build frontend backend ml-service postgres-primary audit-postgres

docker create \
  --name "$PROXY_CONTAINER_NAME" \
  --network "${COMPOSE_PROJECT_NAME}_edge_net" \
  -p "${FULLTEST_HTTP_PORT}:80" \
  -v "$ROOT_DIR/deploy/nginx/nginx.conf:/etc/nginx/nginx.conf:ro" \
  nginx:1.27-alpine >/dev/null
docker network connect "${COMPOSE_PROJECT_NAME}_app_net" "$PROXY_CONTAINER_NAME"
docker start "$PROXY_CONTAINER_NAME" >/dev/null

for _ in $(seq 1 30); do
  if curl -fsS "$PLAYWRIGHT_BASE_URL/actuator/health" >/dev/null; then
    break
  fi
  sleep 5
done

curl -fsS "$PLAYWRIGHT_BASE_URL/actuator/health" >/dev/null

cd "$ROOT_DIR/frontend/e2e"
PLAYWRIGHT_BASE_URL="$PLAYWRIGHT_BASE_URL" npx playwright test
