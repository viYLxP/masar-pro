package com.masar.common;

import java.util.Map;

/**
 * The single exception the service layer throws for expected business errors.
 * الاستثناء الوحيد الذي ترميه طبقة الخدمات عند خطأ متوقع في منطق العمل.
 *
 * <p>It carries an error code plus optional details (such as the available quantity) that are
 * copied into the API response so the UI can show a precise message.</p>
 * <p>يحمل رمز الخطأ وتفاصيل اختيارية (مثل الكمية المتاحة) تُنسخ إلى رد الـ API
 * حتى تعرض الواجهة رسالة دقيقة.</p>
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    // Extra details, exposed as properties of the ProblemDetail response body.
    // تفاصيل إضافية تظهر كخصائص داخل جسم الرد ProblemDetail.
    private final Map<String, Object> details;

    public ApiException(ErrorCode code) {
        this(code, Map.of());
    }

    public ApiException(ErrorCode code, Map<String, Object> details) {
        // Use the code name as the message so it is readable in logs.
        // نستخدم اسم الرمز كرسالة حتى يكون واضحًا في السجلات.
        super(code.name());
        this.code = code;
        this.details = details;
    }

    /**
     * Shortcut for a "not found" error with the resource name and id for easier tracing.
     * اختصار لخطأ "غير موجود" مع اسم المورد ورقمه لتسهيل التتبع.
     */
    public static ApiException notFound(String resource, Object id) {
        return new ApiException(ErrorCode.NOT_FOUND, Map.of("resource", resource, "id", id));
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
