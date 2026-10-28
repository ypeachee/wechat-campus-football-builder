#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=${ROOT_DIR:-/opt/campus-football}
BACKUP_DIR=${BACKUP_DIR:-$ROOT_DIR/backups}
RETENTION_DAYS=${RETENTION_DAYS:-14}
ENV_FILE="$ROOT_DIR/.env.production"
DATE=$(date +%Y%m%d-%H%M%S)
FILE="$BACKUP_DIR/campus-football-$DATE.sql.gz"

mkdir -p "$BACKUP_DIR"
cd "$ROOT_DIR"

docker compose --env-file "$ENV_FILE" -f compose.prod.yaml exec -T mysql \
  sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --quick --routines --events campus_football' \
  | gzip -9 > "$FILE"

find "$BACKUP_DIR" -type f -name 'campus-football-*.sql.gz' -mtime +"$RETENTION_DAYS" -delete

# Optional Aliyun OSS upload. Configure OSS_BACKUP_URI in .env.production.
if [ -f "$ENV_FILE" ]; then
  OSS_BACKUP_URI=$(grep -E '^OSS_BACKUP_URI=' "$ENV_FILE" | tail -n 1 | cut -d= -f2- || true)
  export OSS_BACKUP_URI
fi
if [ -n "${OSS_BACKUP_URI:-}" ] && command -v ossutil >/dev/null 2>&1; then
  ossutil cp -f "$FILE" "$OSS_BACKUP_URI/"
fi

echo "$(date '+%F %T') backup completed: $FILE"