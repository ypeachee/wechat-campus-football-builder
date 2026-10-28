#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=${ROOT_DIR:-/opt/campus-football}
ENV_FILE="$ROOT_DIR/.env.production"
BACKUP_DIR=${BACKUP_DIR:-$ROOT_DIR/backups}
BACKUP_FILE=${1:-}
cd "$ROOT_DIR"

if [ -z "$BACKUP_FILE" ]; then
  BACKUP_FILE=$(find "$BACKUP_DIR" -type f -name 'campus-football-*.sql.gz' | sort | tail -n 1)
fi
if [ -z "$BACKUP_FILE" ] || [ ! -f "$BACKUP_FILE" ]; then
  echo "backup file not found: ${BACKUP_FILE:-<none>}" >&2
  exit 1
fi
if [ -f "$BACKUP_FILE.sha256" ]; then
  sha256sum -c "$BACKUP_FILE.sha256"
fi

RESTORE_DB="campus_football_restore_check_$(date +%s)"
MYSQL_CONTAINER=$(docker compose --env-file "$ENV_FILE" -f compose.prod.yaml ps -q mysql)
if [ -z "$MYSQL_CONTAINER" ]; then
  echo "mysql container is not running" >&2
  exit 1
fi

cleanup() {
  docker compose --env-file "$ENV_FILE" -f compose.prod.yaml exec -T mysql \
    sh -c "MYSQL_PWD=\"\$MYSQL_ROOT_PASSWORD\" mysql -uroot -e 'DROP DATABASE IF EXISTS $RESTORE_DB'" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker compose --env-file "$ENV_FILE" -f compose.prod.yaml exec -T mysql \
  sh -c "MYSQL_PWD=\"\$MYSQL_ROOT_PASSWORD\" mysql -uroot -e 'CREATE DATABASE $RESTORE_DB CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci'"

gunzip -c "$BACKUP_FILE" | docker compose --env-file "$ENV_FILE" -f compose.prod.yaml exec -T mysql \
  sh -c "MYSQL_PWD=\"\$MYSQL_ROOT_PASSWORD\" mysql -uroot $RESTORE_DB"

TABLE_COUNT=$(docker compose --env-file "$ENV_FILE" -f compose.prod.yaml exec -T mysql \
  sh -c "MYSQL_PWD=\"\$MYSQL_ROOT_PASSWORD\" mysql -N -B -uroot -e \"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$RESTORE_DB'\"" | tr -d '\r')

if [ "$TABLE_COUNT" -lt 1 ]; then
  echo "restore drill failed: no tables restored" >&2
  exit 1
fi

echo "$(date '+%F %T') restore drill OK: $BACKUP_FILE tables=$TABLE_COUNT"