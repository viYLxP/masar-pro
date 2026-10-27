package com.masar.common;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes returned by the API.
 * رموز أخطاء ثابتة يرجّعها الـ API ويفهمها البرنامج.
 *
 * <p>The frontend translates these codes into Arabic or English, so the API never returns
 * user-facing text. This keeps server logic independent of the display language.</p>
 * <p>الواجهة الأمامية تترجم هذه الرموز إلى العربية أو الإنجليزية، فلا يرجّع الـ API أي نص
 * موجّه للمستخدم. هذا يفصل منطق الخادم عن لغة العرض.</p>
 */
public enum ErrorCode {
    // Request data is invalid (missing field, negative number, malformed JSON...).
    // بيانات الطلب غير صحيحة (حقل ناقص، رقم سالب، JSON تالف...).
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    // Wrong username/password, or the account is disabled.
    // اسم المستخدم أو كلمة المرور خطأ، أو الحساب موقوف.
    BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    // Logged in, but the role does not allow this action.
    // المستخدم مسجّل دخول لكن صلاحيته لا تسمح بالعملية.
    FORBIDDEN(HttpStatus.FORBIDDEN),
    // The requested record does not exist.
    // السجل المطلوب غير موجود.
    NOT_FOUND(HttpStatus.NOT_FOUND),
    // A technician tried to act on a job that is assigned to someone else.
    // فني حاول يتعامل مع شغل معيّن لفني ثاني.
    NOT_ASSIGNED(HttpStatus.FORBIDDEN),
    // The username is already taken.
    // اسم المستخدم محجوز.
    USERNAME_EXISTS(HttpStatus.CONFLICT),
    // Duplicate service code.
    // رمز الخدمة مكرر.
    CODE_EXISTS(HttpStatus.CONFLICT),
    // Cannot delete because other records reference it (e.g. a service that already has requests).
    // لا يمكن الحذف لأن سجلات أخرى مرتبطة به (مثل خدمة عليها طلبات).
    IN_USE(HttpStatus.CONFLICT),
    // The action is not allowed in the request's current status (e.g. scheduling a request the customer has not accepted).
    // العملية غير مسموحة في حالة الطلب الحالية (مثل جدولة طلب ما وافق عليه العميل).
    INVALID_STATUS(HttpStatus.CONFLICT),
    // A quote with at least one line is needed before it can be sent.
    // لازم يكون فيه عرض سعر فيه بند واحد على الأقل قبل الإرسال.
    QUOTE_REQUIRED(HttpStatus.CONFLICT),
    // The customer tried to accept a quote after its "valid until" date.
    // العميل حاول يقبل عرض سعر بعد تاريخ انتهاء صلاحيته.
    QUOTE_EXPIRED(HttpStatus.CONFLICT),
    // The chosen service is switched off, so it cannot be booked.
    // الخدمة المختارة موقوفة، فما ينفع تنحجز.
    SERVICE_INACTIVE(HttpStatus.CONFLICT),
    // The technician does not offer this service, works in another city, or is not an active technician.
    // الفني ما يقدّم هذي الخدمة، أو يشتغل في مدينة ثانية، أو مو فني نشط.
    TECHNICIAN_NOT_QUALIFIED(HttpStatus.CONFLICT),
    // The technician already has a job at that time (the conflicting reference is in the details).
    // الفني عنده شغل ثاني في نفس الوقت (الرقم المرجعي للشغل المتعارض موجود في التفاصيل).
    TECHNICIAN_BUSY(HttpStatus.CONFLICT),
    // The visit would start in the past or fall outside working hours.
    // الزيارة بتبدأ في وقت فات أو برا ساعات العمل.
    OUTSIDE_WORKING_HOURS(HttpStatus.CONFLICT),
    // Too many requests from the same visitor in a short time on the public site.
    // طلبات كثيرة من نفس الزائر في وقت قصير على الموقع العام.
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    // Someone else changed the same record meanwhile (optimistic locking).
    // شخص آخر عدّل نفس السجل في نفس الوقت (Optimistic Locking).
    CONCURRENT_UPDATE(HttpStatus.CONFLICT),
    // An admin cannot disable or demote their own account, so the system is never locked out.
    // منع المدير من إيقاف حسابه أو تغيير صلاحيته بنفسه حتى لا يُقفل النظام.
    CANNOT_MODIFY_SELF(HttpStatus.CONFLICT),
    // A customer account with this mobile number already exists. / فيه حساب عميل بنفس رقم الجوال.
    PHONE_REGISTERED(HttpStatus.CONFLICT),
    // The current password typed when changing it is wrong. / كلمة المرور الحالية غلط وقت تغييرها.
    WRONG_PASSWORD(HttpStatus.BAD_REQUEST),
    // Paying is not possible now: no accepted quote yet, already paid, or the request was cancelled.
    // الدفع مو متاح الحين: ما فيه عرض مقبول، أو انْدفع قبل، أو الطلب ملغي.
    PAYMENT_NOT_ALLOWED(HttpStatus.CONFLICT),
    // Instalments (Tabby/Tamara) are not offered for this amount or this customer. Details: min and max amount.
    // التقسيط (تابي/تمارا) مو متاح لهالمبلغ أو هالعميل. التفاصيل: أقل وأعلى مبلغ.
    INSTALMENTS_NOT_AVAILABLE(HttpStatus.CONFLICT),
    // The payment gateway did not answer or refused the call. / بوابة الدفع ما ردّت أو رفضت الطلب.
    PAYMENT_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY),
    // Unexpected server error.
    // خطأ غير متوقع في الخادم.
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    // The HTTP status for each code, kept in one place.
    // حالة HTTP المناسبة لكل رمز، محفوظة في مكان واحد.
    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
