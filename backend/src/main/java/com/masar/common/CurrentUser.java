package com.masar.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Reads the current username from the security context.
 * يقرأ اسم المستخدم الحالي من سياق الأمان.
 *
 * <p>Used to record who changed a deal's stage (audit trail).</p>
 * <p>نستخدمه لتسجيل مين غيّر مرحلة الصفقة (سجل تدقيق).</p>
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static String username() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // "system" for work done without a user, such as seeding demo data at startup.
        // "system" للعمليات التي تحدث بدون مستخدم، مثل تعبئة البيانات التجريبية عند التشغيل.
        return auth == null ? "system" : auth.getName();
    }
}
