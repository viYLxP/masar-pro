#!/usr/bin/env bash
# Resets each running app to fresh demo data: empties its database schema, then restarts the API, which recreates the
# tables (Flyway) and reloads the demo data (seeder runs on an empty database). Runs nightly when DEMO_RESET=true.
# يرجّع كل تطبيق شغال لبيانات تجريبية جديدة: يفضّي قاعدة البيانات، بعدين يعيد تشغيل الـ API فينشئ الجداول (Flyway)
# ويحمّل البيانات التجريبية (المُعبّئ يشتغل لما تكون القاعدة فاضية). يشتغل كل ليلة لو DEMO_RESET=true.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
set -a; source .env; set +a
# The nightly run respects DEMO_RESET; running it by hand always resets. / التشغيل الليلي يلتزم بـ DEMO_RESET؛ واليدوي يصفّر دائمًا.
if [[ ! -t 1 && "${DEMO_RESET:-true}" != "true" ]]; then exit 0; fi
for app in masar stockmate clientflow; do
  docker compose ps --status running --services | grep -qx "${app}-db" || continue
  docker compose stop "${app}-backend"
  docker compose exec -T "${app}-db" psql -U "$app" -d "$app" -v ON_ERROR_STOP=1 \
    -q -c 'SET client_min_messages = warning; DROP SCHEMA public CASCADE; CREATE SCHEMA public;'
  docker compose up -d --wait --wait-timeout 300 "${app}-backend"
  echo "[$(date -u +%FT%TZ)] demo reset: $app"
done
