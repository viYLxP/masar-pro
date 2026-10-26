package com.masar.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Converts every error thrown inside controllers into one RFC 9457 (ProblemDetail) response shape.
 * يحوّل أي خطأ داخل الـ Controllers إلى رد موحّد بصيغة RFC 9457 (ProblemDetail).
 *
 * <p>Every error body has a {@code code} property from {@link ErrorCode}, so the UI handles a
 * single shape instead of one per error type.</p>
 * <p>كل رد خطأ فيه خاصية {@code code}، فتتعامل الواجهة مع شكل واحد فقط بدل شكل لكل نوع خطأ.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Business errors we throw ourselves from the services.
    // أخطاء منطق العمل التي نرميها بأنفسنا من الخدمات.
    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApi(ApiException ex) {
        ProblemDetail problem = problem(ex.code());
        // Copy extra details (e.g. "available", "requested") into the body.
        // ننسخ التفاصيل الإضافية (مثل available و requested) إلى جسم الرد.
        ex.details().forEach(problem::setProperty);
        return problem;
    }

    // Bean Validation failures from @Valid request bodies.
    // أخطاء التحقق من المدخلات القادمة من @Valid على الـ DTOs.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        // LinkedHashMap keeps the fields in the order they were reported.
        // LinkedHashMap يحافظ على ترتيب الحقول كما ظهرت.
        Map<String, String> fields = new LinkedHashMap<>();
        // Keep only the first violation per field (e.g. NotBlank or Min) to avoid noisy forms.
        // نأخذ أول خطأ فقط لكل حقل (مثل NotBlank أو Min) حتى لا تزدحم الواجهة بالرسائل.
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getCode()));
        ProblemDetail problem = problem(ErrorCode.VALIDATION_FAILED);
        problem.setProperty("fields", fields);
        return problem;
    }

    // Malformed JSON, or a wrong type in a path/query parameter (text instead of a number).
    // JSON تالف أو نوع خاطئ في باراميتر الرابط (نص بدل رقم).
    // Also covers ?sort=unknownField, which Spring Data reports as PropertyReferenceException.
    // يشمل أيضًا ?sort=حقل_غير_موجود، وSpring Data يبلّغ عنه بـ PropertyReferenceException.
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            PropertyReferenceException.class})
    ProblemDetail handleUnreadable(Exception ex) {
        return problem(ErrorCode.VALIDATION_FAILED);
    }

    // Failed login: never reveal whether the username or the password was wrong (prevents account enumeration).
    // فشل الدخول: لا نوضح هل الخطأ في الاسم أو كلمة المرور حتى لا نساعد المهاجم على تخمين الحسابات.
    @ExceptionHandler({BadCredentialsException.class, DisabledException.class})
    ProblemDetail handleBadCredentials(Exception ex) {
        return problem(ErrorCode.BAD_CREDENTIALS);
    }

    // The user lacks the required role (e.g. a sales rep trying to delete a company).
    // المستخدم لا يملك الصلاحية (مثلًا مندوب يحاول حذف شركة).
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleForbidden(AccessDeniedException ex) {
        return problem(ErrorCode.FORBIDDEN);
    }

    // Concurrent edit: the row version changed after the user loaded it.
    // تعارض تعديل متزامن: نسخة السجل تغيّرت بعد ما قرأه المستخدم.
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return problem(ErrorCode.CONCURRENT_UPDATE);
    }

    // Last safety net for UNIQUE / FOREIGN KEY constraints, in case a race beats our service checks.
    // شبكة أمان أخيرة لقيود UNIQUE و FOREIGN KEY لو سبق طلبٌ متزامن فحوصات الخدمة.
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(ErrorCode.IN_USE);
    }

    // Unknown URL.
    // رابط غير موجود.
    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail handleNoResource(NoResourceFoundException ex) {
        return problem(ErrorCode.NOT_FOUND);
    }

    // HTTP method not supported on this URL (e.g. DELETE on a read-only endpoint).
    // طريقة HTTP غير مدعومة على هذا الرابط.
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ProblemDetail handleMethod(HttpRequestMethodNotSupportedException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.METHOD_NOT_ALLOWED);
        problem.setProperty("code", ErrorCode.VALIDATION_FAILED.name());
        return problem;
    }

    // Anything unexpected: log it in full, but return a generic body with no internals
    // (no stack trace, no table names) so we do not leak information about the system.
    // أي خطأ غير متوقع: نسجّله كاملًا، ونرجّع ردًا عامًا بدون تفاصيل داخلية
    // (لا stack trace ولا أسماء جداول) حتى لا نكشف معلومات عن النظام.
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return problem(ErrorCode.INTERNAL_ERROR);
    }

    /**
     * Builds a base ProblemDetail from an error code and its HTTP status.
     * يبني ProblemDetail أساسي من رمز الخطأ وحالة HTTP المرتبطة به.
     */
    private static ProblemDetail problem(ErrorCode code) {
        HttpStatus status = code.status();
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code.name());
        return problem;
    }
}
