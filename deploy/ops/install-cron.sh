#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=${ROOT_DIR:-/opt/campus-football}
chmod +x "$ROOT_DIR/deploy/ops/mysql-backup.sh" "$ROOT_DIR/deploy/ops/health-check.sh"

cat > /etc/cron.d/campus-football <<EOF
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/sbin:/bin:/usr/sbin:/usr/bin
17 3 * * * root ROOT_DIR=$ROOT_DIR $ROOT_DIR/deploy/ops/mysql-backup.sh >> /var/log/campus-football-backup.log 2>&1
*/5 * * * * root ROOT_DIR=$ROOT_DIR $ROOT_DIR/deploy/ops/health-check.sh >> /var/log/campus-football-health.log 2>&1
EOF
chmod 644 /etc/cron.d/campus-football

cat > /etc/logrotate.d/campus-football <<EOF
/var/log/campus-football-*.log {
  daily
  rotate 14
  compress
  missingok
  notifempty
  copytruncate
}
EOF

echo "Installed /etc/cron.d/campus-football and log rotation."