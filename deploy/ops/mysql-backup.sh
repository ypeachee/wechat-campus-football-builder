#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=${ROOT_DIR:-/opt/campus-football}
BACKUP_DIR=${BACKUP_DIR:-$ROOT_DIR/backups}
RETENTION_DAYS=${RETENTION_DAYS:-14}
ENV_FILE="$ROOT_DIR/.env.production"
DATE=$(date +%Y%m%d-%H%M%S)
FILE="$BACKUP_DIR/campus-football-$DATE.sql.gz"
CHECKSUM="$FILE.sha256"

mkdir -p "$BACKUP_DIR"
cd "$ROOT_DIR"

exec 9>/var/lock/campus-football-backup.lock
if ! flock -n 9; then
  echo "$(date '+%F %T') backup skipped: another run is active"
  exit 0
fi

env_value() {
  local key=$1
  grep -E "^${key}=" "$ENV_FILE" 2>/dev/null | tail -n 1 | cut -d= -f2- || true
}

OSS_BACKUP_URI=$(env_value OSS_BACKUP_URI)
MYSQL_DATABASE=$(env_value MYSQL_DATABASE)
MYSQL_DATABASE=${MYSQL_DATABASE:-campus_football}

docker compose --env-file "$ENV_FILE" -f compose.prod.yaml exec -T mysql \
  sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --quick --routines --events "$MYSQL_DATABASE"' \
  | gzip -9 > "$FILE"

if [ ! -s "$FILE" ]; then
  rm -f "$FILE"
  echo "$(date '+%F %T') ERROR: backup file is empty" >&2
  exit 1
fi
sha256sum "$FILE" > "$CHECKSUM"

find "$BACKUP_DIR" -type f \( -name 'campus-football-*.sql.gz' -o -name 'campus-football-*.sql.gz.sha256' \) \
  -mtime +"$RETENTION_DAYS" -delete

if [ -n "${OSS_BACKUP_URI:-}" ]; then
  if ! command -v ossutil >/dev/null 2>&1; then
    echo "$(date '+%F %T') ERROR: OSS_BACKUP_URI is configured but ossutil is not installed" >&2
    exit 1
  fi
  ossutil cp -f "$FILE" "$OSS_BACKUP_URI/$(basename "$FILE")"
  ossutil cp -f "$CHECKSUM" "$OSS_BACKUP_URI/$(basename "$CHECKSUM")"
fi

echo "$(date '+%F %T') backup completed: $FILE"