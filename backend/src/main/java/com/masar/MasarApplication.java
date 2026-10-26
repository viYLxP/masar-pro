package com.masar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the Masar Pro REST API.
 * نقطة تشغيل الواجهة الخلفية (REST API) لتطبيق Masar Pro.
 *
 * <p>{@code @SpringBootApplication} enables auto-configuration and scans every package under
 * {@code com.masar} for controllers, services and repositories.
 * {@code @ConfigurationPropertiesScan} binds the {@code masar.*} settings in application.yml
 * to {@link com.masar.config.AppProperties}.</p>
 * <p>{@code @SpringBootApplication} يفعّل الإعداد التلقائي ويفحص كل الحزم تحت {@code com.masar}
 * بحثًا عن الـ Controllers والـ Services والـ Repositories.
 * و{@code @ConfigurationPropertiesScan} يربط إعدادات {@code masar.*} بكلاس AppProperties.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MasarApplication {

    public static void main(String[] args) {
        // Boot the whole Spring context: database connection, Flyway migrations, then the web server.
        // نشغّل سياق Spring كاملًا: الاتصال بقاعدة البيانات، ترحيلات Flyway، ثم خادم الويب.
        SpringApplication.run(MasarApplication.class, args);
    }
}
