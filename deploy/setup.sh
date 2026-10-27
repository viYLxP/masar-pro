#!/usr/bin/env bash
# First-time setup on a fresh Ubuntu server (Oracle Cloud, Contabo, OVH, ...). Safe to run again.
# It installs Docker, opens ports 80/443, adds swap on small servers, creates deploy/.env with random secrets,
# schedules backups / demo reset / updates, then starts everything.
# الإعداد لأول مرة على سيرفر Ubuntu جديد (Oracle Cloud أو Contabo أو OVH...). تشغيله مرة ثانية آمن.
# يثبّت Docker، ويفتح المنافذ 80/443، ويضيف swap للسيرفرات الصغيرة، وينشئ deploy/.env بأسرار عشوائية،
# ويجدول النسخ الاحتياطي وتصفير العرض والتحديث، وبعدها يشغّل كل شي.
#
# Usage / الاستخدام:
#   sudo git clone https://github.com/viYLxP/masar-pro.git /opt/masar-pro
#   sudo bash /opt/masar-pro/deploy/setup.sh
set -euo pipefail

if [[ $EUID -ne 0 ]]; then echo "Run with sudo / شغّله بـ sudo"; exit 1; fi
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

say() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }

# 1) Docker (official convenience script; includes the compose plugin). / Docker (السكربت الرسمي، ومعه compose).
if ! command -v docker >/dev/null 2>&1; then
  say "Installing Docker / تثبيت Docker"
  curl -fsSL https://get.docker.com | sh
fi
systemctl enable --now docker >/dev/null

# 2) Oracle Cloud's Ubuntu images reject every port except 22 in iptables; open 80 and 443 there too.
#    (The cloud console's Security List must also allow them; see README.)
#    صور Ubuntu في Oracle Cloud ترفض كل المنافذ ما عدا 22 في iptables؛ نفتح 80 و443 هناك.
#    (ولازم تسمح لها كمان في Security List من لوحة Oracle؛ شوف README.)
if iptables -S INPUT 2>/dev/null | grep -q -- '-j REJECT'; then
  say "Opening ports 80/443 in iptables / فتح المنافذ 80/443"
  for port in 443 80; do
    iptables -C INPUT -p tcp --dport "$port" -j ACCEPT 2>/dev/null || iptables -I INPUT 1 -p tcp --dport "$port" -j ACCEPT
  done
  iptables -C INPUT -p udp --dport 443 -j ACCEPT 2>/dev/null || iptables -I INPUT 1 -p udp --dport 443 -j ACCEPT
  if command -v netfilter-persistent >/dev/null 2>&1; then netfilter-persistent save >/dev/null; fi
fi

# 3) Swap on servers under 6 GB RAM, so a memory spike slows things down instead of killing a container.
#    swap للسيرفرات الأقل من 6 جيجا رام، حتى ارتفاع الذاكرة يبطّئ بدل ما يوقف حاوية.
mem_kb=$(awk '/MemTotal/ {print $2}' /proc/meminfo)
if (( mem_kb < 6000000 )) && ! swapon --show | grep -q .; then
  say "Adding 2 GB swap / إضافة 2 جيجا swap"
  fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile >/dev/null && swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

# 4) deploy/.env with random secrets; existing values are never overwritten.
#    deploy/.env بأسرار عشوائية؛ والقيم الموجودة ما تنكتب فوقها أبدًا.
[[ -f .env ]] || cp .env.example .env
chmod 600 .env
set_if_empty() {
  local key="$1" value="$2"
  if grep -qE "^${key}=$" .env; then sed -i "s|^${key}=$|${key}=${value}|" .env; fi
}
secret() { openssl rand -base64 48 | tr -d '\n/+='; }
for app in MASAR STOCKMATE CLIENTFLOW; do
  set_if_empty "${app}_JWT_SECRET" "$(secret)"
  set_if_empty "${app}_DB_PASSWORD" "$(openssl rand -hex 24)"
done
# Readable but strong, and it always has upper, lower, digit and symbol. / مقروءة وقوية، وفيها دائمًا حرف كبير وصغير ورقم ورمز.
set_if_empty ADMIN_PASSWORD "Admin-$(openssl rand -hex 6)@Pro1"

if grep -qE '^MASAR_HOST=$' .env; then
  ip=$(curl -4 -fsS --max-time 10 https://api.ipify.org || curl -4 -fsS --max-time 10 https://ifconfig.me)
  dashed=${ip//./-}
  say "No domain set: using sslip.io names for $ip / ما فيه دومين: نستخدم أسماء sslip.io"
  set_if_empty MASAR_HOST "masar.${dashed}.sslip.io"
  set_if_empty STOCKMATE_HOST "stockmate.${dashed}.sslip.io"
  set_if_empty CLIENTFLOW_HOST "clientflow.${dashed}.sslip.io"
fi

# 5) Nightly jobs (server clock is UTC on cloud images; 00:00 UTC = 03:00 Riyadh).
#    مهام ليلية (ساعة السيرفر UTC في صور السحابة؛ 00:00 UTC = 3 الفجر بتوقيت الرياض).
cat > /etc/cron.d/pro-apps <<CRON
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
45 23 * * * root $DIR/backup.sh >> /var/log/pro-apps.log 2>&1
0 0 * * * root $DIR/demo-reset.sh >> /var/log/pro-apps.log 2>&1
30 0 * * * root $DIR/update.sh >> /var/log/pro-apps.log 2>&1
CRON
chmod +x "$DIR"/*.sh

# 6) Start. The first start pulls images and lets each API create its tables, which takes a few minutes.
#    التشغيل. أول مرة يسحب الصور وكل API ينشئ جداوله، وهذا ياخذ كم دقيقة.
say "Starting the apps (first time takes a few minutes) / تشغيل التطبيقات (أول مرة تاخذ كم دقيقة)"
docker compose pull
docker compose up -d --wait --wait-timeout 600

# shellcheck disable=SC1091
set -a; source .env; set +a
say "Done / تم"
echo "  Masar Pro:      https://${MASAR_HOST}"
echo "  StockMate Pro:  https://${STOCKMATE_HOST}"
echo "  ClientFlow Pro: https://${CLIENTFLOW_HOST}"
echo "  Admin password (all three, keep it private) / كلمة مرور المدير (للثلاثة، خلها سرية): ${ADMIN_PASSWORD}"
echo "  The first HTTPS visit may take ~30 s while Caddy gets the certificates. / أول فتح ممكن ياخذ 30 ثانية لين تجهز الشهادات."
