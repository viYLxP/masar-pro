#!/usr/bin/env bash
# Pull the newest server files and images, restart only the containers whose image changed, and clean old images.
# يسحب أحدث ملفات السيرفر والصور، ويعيد تشغيل الحاويات اللي تغيرت صورتها بس، وينظف الصور القديمة.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
set -a; source .env; set +a
# The nightly run respects AUTO_UPDATE; running it by hand always updates.
# التشغيل الليلي يلتزم بـ AUTO_UPDATE؛ والتشغيل اليدوي يحدّث دائمًا.
if [[ ! -t 1 && "${AUTO_UPDATE:-true}" != "true" ]]; then exit 0; fi
echo "[$(date -u +%FT%TZ)] update"
git -C .. pull --ff-only --quiet || echo "git pull skipped (local changes?) / تم تخطي git pull"
docker compose pull --quiet
docker compose up -d --remove-orphans --wait --wait-timeout 600
docker image prune -f >/dev/null
