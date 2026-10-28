#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=${ROOT_DIR:-/opt/campus-football}
ENV_FILE="$ROOT_DIR/.env.production"
API_DOMAIN=${API_DOMAIN:-api.cugbsoccer.xyz}
HEALTH_URL=${HEALTH_URL:-https://${API_DOMAIN}/api/v1/seasons}
DISK_LIMIT=${DISK_LIMIT:-85}
MEMORY_MIN_KB=${MEMORY_MIN_KB:-65536}
CERT_DAYS_MIN=${CERT_DAYS_MIN:-14}
cd "$ROOT_DIR"

env_value() {
  local key=$1
  grep -E "^${key}=" "$ENV_FILE" 2>/dev/null | tail -n 1 | cut -d= -f2- || true
}

ALERT_WEBHOOK_URL=$(env_value ALERT_WEBHOOK_URL)
ISSUES=()
add_issue() { ISSUES+=("$1"); }

send_alert() {
  [ -n "${ALERT_WEBHOOK_URL:-}" ] || return 0
  local message
  message=$(printf '%s\n' "$@" | sed ':a;N;$!ba;s/\n/\\n/g; s/"/\\"/g')
  curl -fsS --max-time 10 -H 'Content-Type: application/json' \
    -d "{\"msgtype\":\"text\",\"text\":{\"content\":\"${message}\"}}" \
    "$ALERT_WEBHOOK_URL" >/dev/null || true
}

if ! curl -fsS --max-time 15 "$HEALTH_URL" >/dev/null; then
  add_issue "API health check failed: $HEALTH_URL"
fi

DISK_USED=$(df -P / | awk 'NR==2 {gsub("%", "", $5); print $5}')
if [ "$DISK_USED" -ge "$DISK_LIMIT" ]; then
  add_issue "root disk usage is ${DISK_USED}%"
fi

MEM_AVAILABLE=$(awk '/MemAvailable/ {print $2}' /proc/meminfo)
if [ "$MEM_AVAILABLE" -lt "$MEMORY_MIN_KB" ]; then
  add_issue "available memory below ${MEMORY_MIN_KB} KB"
fi

CORES=$(nproc)
LOAD_1=$(awk '{print $1}' /proc/loadavg)
LOAD_LIMIT=$((CORES * 2))
if awk -v load="$LOAD_1" -v limit="$LOAD_LIMIT" 'BEGIN { exit !(load > limit) }'; then
  add_issue "load average ${LOAD_1} exceeds ${LOAD_LIMIT}"
fi

API_CONTAINER=$(docker compose --env-file "$ENV_FILE" -f compose.prod.yaml ps -q api 2>/dev/null || true)
MYSQL_CONTAINER=$(docker compose --env-file "$ENV_FILE" -f compose.prod.yaml ps -q mysql 2>/dev/null || true)
for container in "$API_CONTAINER" "$MYSQL_CONTAINER"; do
  if [ -z "$container" ]; then
    add_issue "required container is missing"
    continue
  fi
  state=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || echo missing)
  if [ "$state" != "healthy" ] && [ "$state" != "running" ]; then
    add_issue "container $container state is $state"
  fi
done

if command -v openssl >/dev/null 2>&1; then
  CERT_END=$(timeout 12 openssl s_client -connect "${API_DOMAIN}:443" -servername "$API_DOMAIN" </dev/null 2>/dev/null \
    | openssl x509 -noout -enddate 2>/dev/null | cut -d= -f2- || true)
  if [ -n "$CERT_END" ]; then
    CERT_SECONDS=$(( $(date -d "$CERT_END" +%s) - $(date +%s) ))
    CERT_DAYS=$((CERT_SECONDS / 86400))
    if [ "$CERT_DAYS" -lt "$CERT_DAYS_MIN" ]; then
      add_issue "HTTPS certificate expires in ${CERT_DAYS} days"
    fi
  else
    add_issue "unable to read HTTPS certificate expiry"
  fi
fi

if [ "${#ISSUES[@]}" -gt 0 ]; then
  printf '%s\n' "$(date '+%F %T') ERROR"
  printf ' - %s\n' "${ISSUES[@]}"
  send_alert "CUGB校园足球监控告警" "${ISSUES[@]}"
  exit 1
fi

echo "$(date '+%F %T') OK api=$HEALTH_URL disk=${DISK_USED}% mem_available_kb=$MEM_AVAILABLE load=$LOAD_1 cert_days=${CERT_DAYS:-unknown}"