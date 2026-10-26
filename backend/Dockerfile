# ---------- Stage 1: build the jar with Maven ----------
# ---------- المرحلة 1: بناء ملف jar باستخدام Maven ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Copy pom.xml first and download dependencies, so Docker caches this layer and rebuilds are fast
# when only source code changes.
# ننسخ pom.xml أولًا ونحمّل المكتبات، فيحتفظ Docker بهذي الطبقة وتصير إعادة البناء سريعة
# لما يتغير الكود فقط.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Tests run in CI against a real PostgreSQL; the image build skips them to stay fast.
# الاختبارات تشتغل في CI على PostgreSQL حقيقي، وبناء الصورة يتخطاها حتى يكون سريع.
RUN mvn -B -q -DskipTests package

# ---------- Stage 2: small runtime image (JRE only, no Maven, no source) ----------
# ---------- المرحلة 2: صورة تشغيل صغيرة (JRE فقط، بدون Maven ولا كود مصدري) ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Run as an unprivileged user instead of root, which limits damage if the app is ever compromised.
# نشغّل التطبيق بمستخدم بدون صلاحيات بدل root، وهذا يقلل الضرر لو انخترق التطبيق.
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/masar-api-*.jar app.jar
USER app
EXPOSE 8080
# The container reports healthy only once the API answers, so docker compose can wait for it.
# الحاوية تعتبر سليمة فقط لما يرد الـ API، فيقدر docker compose ينتظرها.
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q UP || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
