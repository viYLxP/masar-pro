# Production deployment / الرفع على سيرفر

One small Linux server runs all three Pro apps (Masar, StockMate, ClientFlow) behind HTTPS.
سيرفر Linux صغير واحد يشغّل مشاريع Pro الثلاثة (مسار، StockMate، ClientFlow) خلف HTTPS.

```
visitor ──https──▶ Caddy (80/443, free Let's Encrypt certificates)
                     ├─▶ masar-frontend (nginx) ──/api──▶ masar-backend (Spring Boot) ──▶ masar-db (PostgreSQL)
                     ├─▶ stockmate-frontend      ──/api──▶ stockmate-backend           ──▶ stockmate-db
                     └─▶ clientflow-frontend     ──/api──▶ clientflow-backend          ──▶ clientflow-db
```

- **Images, not builds.** After CI passes on `main`, `.github/workflows/release.yml` in each repo publishes
  `ghcr.io/viylxp/<app>-backend` and `-frontend` for amd64 and arm64. The server only pulls them.
- **Isolation.** Each app has its own private Docker network and database. Only Caddy publishes ports; databases
  and APIs are never reachable from the internet.
- **Real visitor IPs.** Caddy sets `X-Forwarded-For`; `nginx-real-ip.conf` makes nginx trust it, so the per-IP
  anti-spam limits in each API count visitors separately.
- **Nightly jobs** (`/etc/cron.d/pro-apps`): 02:45 Riyadh `backup.sh` (14 days kept), 03:00 `demo-reset.sh`
  (fresh demo data, if `DEMO_RESET=true`), 03:30 `update.sh` (newest images, if `AUTO_UPDATE=true`).
- **Memory.** Each API is capped at 700 MB and each database at 384 MB; the whole stack fits in about 3.5 GB.
  `setup.sh` adds 2 GB swap on servers under 6 GB.

## Requirements / المتطلبات

- Ubuntu 22.04 or 24.04, at least 2 vCPU and 4 GB RAM (amd64 or ARM). / Ubuntu ‏22.04 أو 24.04، على الأقل 2 معالج و4 جيجا رام.
- Ports 22, 80 and 443 open in the provider's firewall. / المنافذ 22 و80 و443 مفتوحة في جدار الحماية عند المزوّد.
- Optional: a domain. Without one, free `*.sslip.io` names are used. / اختياري: دومين. بدونه نستخدم أسماء `sslip.io` مجانية.

## First install / أول تثبيت

```bash
sudo git clone https://github.com/viYLxP/masar-pro.git /opt/masar-pro
sudo bash /opt/masar-pro/deploy/setup.sh
```

It prints the three links and the private admin password. / يطبع الروابط الثلاثة وكلمة مرور المدير الخاصة.

## Everyday commands / أوامر يومية

Run inside `/opt/masar-pro/deploy`. / شغّلها داخل `/opt/masar-pro/deploy`.

| Task / المهمة | Command / الأمر |
|---|---|
| Status / الحالة | `sudo docker compose ps` |
| Logs of one app / سجلات تطبيق | `sudo docker compose logs -f --tail=100 masar-backend` |
| Update now / تحديث الحين | `sudo ./update.sh` |
| Reset demo data now / تصفير العرض الحين | `sudo ./demo-reset.sh` |
| Backup now / نسخة احتياطية الحين | `sudo ./backup.sh` |
| Change settings / تغيير الإعدادات | `sudo nano .env` then `sudo docker compose up -d` |
| Roll back an app / الرجوع لنسخة | set `MASAR_TAG=<commit sha>` in `.env`, then `sudo docker compose up -d` |

## Own domain / دومين خاص

Add A records (`masar`, `stockmate`, `clientflow` → server IP), put the names in `.env`
(`MASAR_HOST=masar.example.com` ...), then `sudo docker compose up -d`. Caddy gets the new certificates by itself.
أضف سجلات A (`masar` و`stockmate` و`clientflow` ← IP السيرفر)، واكتب الأسماء في `.env`، بعدين
`sudo docker compose up -d`. Caddy يجيب الشهادات الجديدة بنفسه.

## Payments / المدفوعات

All apps start with the built-in `SANDBOX` gateway (no real money, no account needed). To use real test gateways,
set `<APP>_CARD_PAYMENT_PROVIDER` and fill the matching `<APP>_..._KEY` values in `.env` with **test** keys, then
`sudo docker compose up -d`. Customers return to `https://<host>/pay/...` by themselves (each app's `PUBLIC_URL`).
Webhooks are not implemented yet.
كل التطبيقات تبدأ ببوابة `SANDBOX` المدمجة (بدون فلوس حقيقية ولا حساب). للبوابات التجريبية الحقيقية، عبّ مفاتيح
**TEST** في `.env` (شوف "Payments setup" في README كل تطبيق). الـ webhooks غير مطبقة للحين.

## Oracle Cloud notes / ملاحظات Oracle Cloud

- Create the VM as **Ubuntu**, shape **VM.Standard.A1.Flex** (Always Free eligible; 2 OCPU / 12 GB is plenty).
- In *Networking → Virtual cloud network → Security List*, add ingress rules for TCP 80 and 443 from `0.0.0.0/0`.
- `setup.sh` also opens 80/443 in the VM's own iptables, which Oracle's Ubuntu images close by default.
