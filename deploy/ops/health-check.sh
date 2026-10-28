#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=${ROOT_DIR:-/opt/campus-football}
ENV_FILE="$ROOT_DIR/.env.production"
HEALTH_URL=${HEALTH_URL:-https://api.cugbsoccer.xyz/api/v1/seasons}
cd "$ROOT_DIR"

FAILED=0
if ! curl -fsS --max-time 15 "$HEALTH_URL" >/dev/null; then
  echo "$(date '+%F %T') ERROR: API health check failed: $HEALTH_URL"
  FAILED=1
fi

DISK_USED=$(df -P / | awk 'NR==2 {gsub("%", "", $5); print $5}')
if [ "$DISK_USED" -ge 85 ]; then
  echo "$(date '+%F %T') WARNING: root disk usage is ${DISK_USED}%"
  FAILED=1
fi

MEM_AVAILABLE=$(awk '/MemAvailable/ {print $2}' /proc/meminfo)
if [ "$MEM_AVAILABLE" -lt 65536 ]; then
  echo "$(date '+%F %T') WARNING: available memory below 64 MB"
  FAILED=1
fi

if ! docker compose --env-file "$ENV_FILE" -f compose.prod.yaml ps --status running | grep -q campus-football-api-1; then
  echo "$(date '+%F %T') ERROR: API container is not running"
  FAILED=1
fi

if [ "$FAILED" -eq 0 ]; then
  echo "$(date '+%F %T') OK api=$HEALTH_URL disk=${DISK_USED}% mem_available_kb=$MEM_AVAILABLE"
fi
exit "$FAILED"