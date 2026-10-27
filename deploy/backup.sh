#!/usr/bin/env bash
# Daily compressed dump of each running app database into deploy/backups, keeping the last 14 days.
# نسخة يومية مضغوطة من قاعدة بيانات كل تطبيق شغال في deploy/backups، ونحتفظ بآخر 14 يوم.
# Restore / الاسترجاع:  gunzip -c backups/masar-<date>.sql.gz | docker compose exec -T masar-db psql -U masar -d masar
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
mkdir -p backups && chmod 700 backups
stamp=$(date -u +%Y%m%d-%H%M)
for app in masar stockmate clientflow; do
  # Skip apps that are not running (disabled in COMPOSE_PROFILES). / نتخطى التطبيقات الموقفة.
  docker compose ps --status running --services | grep -qx "${app}-db" || continue
  docker compose exec -T "${app}-db" pg_dump -U "$app" -d "$app" --clean --if-exists | gzip > "backups/${app}-${stamp}.sql.gz"
done
find backups -name '*.sql.gz' -mtime +14 -delete
echo "[$(date -u +%FT%TZ)] backup ok"
