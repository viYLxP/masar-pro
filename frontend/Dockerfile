# ---------- Stage 1: build the Angular app ----------
# ---------- المرحلة 1: بناء تطبيق Angular ----------
# The build stage runs on the machine's own CPU even when producing an ARM image, because its output (jar / static files)
# is the same on every CPU; only the runtime stage below is per-architecture. This keeps multi-arch builds fast.
# مرحلة البناء تشتغل على معالج الجهاز نفسه حتى لو نبني صورة ARM، لأن ناتجها (jar أو ملفات ثابتة) واحد لكل المعالجات؛
# والمرحلة الثانية فقط تختلف حسب المعالج. وهذا يخلي بناء الصور لأكثر من معالج سريع.
FROM --platform=$BUILDPLATFORM node:22-alpine AS build
WORKDIR /app
# Install dependencies first (cached layer), then copy the source.
# نثبّت المكتبات أولًا (طبقة محفوظة)، بعدين ننسخ الكود.
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# ---------- Stage 2: serve the static files with nginx ----------
# ---------- المرحلة 2: تقديم الملفات الثابتة عبر nginx ----------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
# 127.0.0.1, not "localhost": inside the container localhost can resolve to ::1 (IPv6) while nginx listens on IPv4 only,
# which made the check fail on GitHub's runners even though the site was up.
# نستخدم 127.0.0.1 وليس "localhost": داخل الحاوية ممكن localhost يتحول لـ ::1 (IPv6) بينما nginx يستمع على IPv4 فقط،
# وهذا خلّى الفحص يفشل في أجهزة GitHub مع إن الموقع شغال.
HEALTHCHECK --interval=10s --timeout=3s --retries=5 CMD wget -qO- http://127.0.0.1/ >/dev/null || exit 1
