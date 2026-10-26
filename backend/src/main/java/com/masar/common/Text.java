package com.masar.common;

/**
 * Small string helpers shared by the services.
 * دوال نصوص صغيرة تستخدمها الخدمات.
 */
public final class Text {

    private Text() {
    }

    // Optional text fields are stored as NULL instead of "" or "   ", so "is empty" has one meaning in SQL.
    // الحقول النصية الاختيارية تنحفظ NULL بدل "" أو "   "، حتى يكون معنى "فاضي" واحد في SQL.
    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Builds a case-insensitive LIKE pattern, or null when there is nothing to search for.
     * يبني نمط LIKE بدون حساسية لحالة الأحرف، أو null لو ما فيه شي نبحث عنه.
     *
     * Escapes LIKE wildcards so a user typing "%" or "_" searches for them literally (use '\\' as escape char).
     * يهرّب رموز LIKE حتى لو كتب المستخدم "%" أو "_" يبحث عنها كنص عادي (رمز الهروب '\\').
     */
    public static String likePattern(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        return "%" + q.trim().toLowerCase()
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
