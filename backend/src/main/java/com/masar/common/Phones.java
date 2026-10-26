package com.masar.common;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Saudi mobile numbers: accepts the common ways people type them and stores one form.
 * أرقام الجوال السعودية: يقبل الطرق المعتادة لكتابتها ويحفظها بشكل واحد.
 *
 * <p>"0551234567", "551234567", "+966 55 123 4567" and "00966551234567" all become "966551234567". Storing one form
 * means the tracking page finds the request however the customer typed the number the second time.</p>
 * <p>"0551234567" و "551234567" و "+966 55 123 4567" و "00966551234567" كلها تصير "966551234567". حفظها بشكل واحد
 * يعني إن صفحة التتبع تلقى الطلب مهما كتب العميل الرقم المرة الثانية.</p>
 */
public final class Phones {

    // After removing spaces, dashes and brackets: optional country code, then 5 and 8 digits.
    // بعد حذف المسافات والشرطات والأقواس: رمز الدولة اختياري، ثم 5 وبعدها 8 أرقام.
    private static final Pattern SAUDI_MOBILE = Pattern.compile("^(?:\\+966|00966|966|0)?(5\\d{8})$");

    private Phones() {
    }

    /**
     * The normalised number, or null when it is not a Saudi mobile number.
     * الرقم بالشكل الموحّد، أو null لو ما كان جوال سعودي.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        var matcher = SAUDI_MOBILE.matcher(raw.replaceAll("[\\s()-]", ""));
        return matcher.matches() ? "966" + matcher.group(1) : null;
    }

    /**
     * Like {@link #normalize} but fails with a field error the form can show next to the input.
     * مثل normalize لكن يرمي خطأ على الحقل تعرضه الواجهة جنب الخانة.
     */
    public static String require(String raw, String field) {
        String phone = normalize(raw);
        if (phone == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of(field, "Phone")));
        }
        return phone;
    }
}
