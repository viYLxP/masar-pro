package com.masar.config;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed application settings bound from the {@code masar.*} section of application.yml.
 * إعدادات التطبيق مربوطة بأنواع واضحة من قسم {@code masar.*} في application.yml.
 *
 * <p>{@code @Validated} makes the app refuse to start with an invalid configuration
 * (for example a JWT secret that is too short), instead of failing later at runtime.</p>
 * <p>{@code @Validated} يمنع التطبيق من التشغيل إذا كانت الإعدادات غير صالحة
 * (مثل مفتاح JWT قصير)، بدل ما يفشل لاحقًا أثناء الاستخدام.</p>
 */
@Validated
@ConfigurationProperties(prefix = "masar")
public record AppProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Cors cors,
        // Business time zone used for "today", "this month", working hours and quote expiry (Asia/Riyadh by default).
        // المنطقة الزمنية للنشاط وتُستخدم لتحديد "اليوم" و"هذا الشهر" وساعات العمل وانتهاء عروض الأسعار (الرياض افتراضيًا).
        @NotNull ZoneId timeZone,
        @Valid @NotNull Seed seed,
        @Valid @NotNull RateLimit rateLimit) {

    /**
     * JWT signing settings.
     * إعدادات توقيع رموز JWT.
     *
     * @param secret     HMAC-SHA256 key; at least 32 chars (256 bits) as required by the algorithm
     *                   / مفتاح HMAC-SHA256 ولازم 32 حرفًا على الأقل (256 بت) حسب متطلبات الخوارزمية
     * @param expiration how long a token stays valid / مدة صلاحية الرمز
     */
    public record Jwt(@NotNull @Size(min = 32) String secret, @NotNull Duration expiration) {
    }

    /**
     * Allowed browser origins for CORS (the Angular dev server in development).
     * مصادر المتصفح المسموح لها عبر CORS (خادم Angular أثناء التطوير).
     */
    public record Cors(@NotNull List<String> allowedOrigins) {
    }

    /**
     * Demo data settings. Passwords come from environment variables, never hard-coded in production.
     * إعدادات البيانات التجريبية. كلمات المرور تأتي من متغيرات البيئة ولا تُكتب في الكود للإنتاج.
     */
    public record Seed(boolean enabled, String adminPassword, String staffPassword) {
    }

    /**
     * Per-IP limits for the public endpoints within one time window.
     * حدود لكل عنوان IP على الروابط العامة خلال فترة زمنية وحدة.
     *
     * @param window   length of the counting window / طول فترة العدّ
     * @param bookings new booking requests allowed per window / عدد طلبات الحجز المسموحة في الفترة
     * @param lookups  tracking, accept and reject calls allowed per window / عدد عمليات التتبع والقبول والرفض المسموحة
     */
    public record RateLimit(@NotNull Duration window, @Min(1) int bookings, @Min(1) int lookups) {
    }
}
