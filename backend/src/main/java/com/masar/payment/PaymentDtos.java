package com.masar.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.masar.catalog.ServiceTypeDtos.ServiceRef;
import com.masar.request.PaymentStatus;

/**
 * Request/response shapes for payments.
 * أشكال الطلب والرد للمدفوعات.
 */
public final class PaymentDtos {

    private PaymentDtos() {
    }

    // Where to send the customer to pay. / وين ننقل العميل عشان يدفع.
    public record CheckoutResponse(UUID paymentId, String checkoutUrl) {
    }

    // A guest proves the request is theirs with the phone, like on the tracking page.
    // الزائر يثبت إن الطلب له بالجوال، مثل صفحة التتبع.
    public record GuestPayRequest(@NotBlank @Size(max = 20) String phone, @NotNull Payment.Method method) {
    }

    public record PayRequest(@NotNull Payment.Method method) {
    }

    // One instalment of a Tabby/Tamara plan. / دفعة وحدة من خطة تابي أو تمارا.
    public record Instalment(int number, BigDecimal amount, LocalDate dueDate) {
    }

    // One payment as the office sees it. / دفعة وحدة مثل ما يشوفها المكتب.
    public record PaymentView(UUID id, Payment.Method method, Payment.Provider provider, Payment.Status status,
                              BigDecimal amount, String currency, String cardBrand, String cardLast4,
                              String failureReason, String recordedBy, Instant createdAt, Instant paidAt,
                              Instant refundedAt) {

        public static PaymentView of(Payment p) {
            return new PaymentView(p.getId(), p.getMethod(), p.getProvider(), p.getStatus(), p.getAmount(),
                    p.getCurrency(), p.getCardBrand(), p.getCardLast4(), p.getFailureReason(), p.getRecordedBy(),
                    p.getCreatedAt(), p.getPaidAt(), p.getRefundedAt());
        }
    }

    /**
     * The payment part of the customer's view of a request: what is due, and how it was paid.
     * جزء الدفع في عرض العميل للطلب: كم المطلوب، وكيف انْدفع.
     */
    /**
     * @param methods     online methods offered for this request (instalments only within their limits)
     *                    / الطرق المتاحة أونلاين لهالطلب (التقسيط بس داخل حدوده)
     * @param instalments the plan Tabby/Tamara would offer, to show "4 payments of ..." / خطة التقسيط لعرض "4 دفعات ..."
     */
    public record PaymentSummary(PaymentStatus status, boolean payable, BigDecimal amountDue,
                                 List<Payment.Method> methods, List<Instalment> instalments,
                                 Payment.Method method, String cardBrand, String cardLast4, Instant paidAt) {
    }

    /**
     * What the payment page shows. No phone, no address: the link may be shared by mistake.
     * اللي تعرضه صفحة الدفع. بدون جوال ولا عنوان: الرابط ممكن ينشارك بالغلط.
     */
    public record CheckoutPage(UUID id, Payment.Method method, Payment.Status status, Payment.Provider provider,
                               BigDecimal amount, String currency, String reference, ServiceRef service,
                               List<Instalment> instalments, String failureReason, String cardBrand,
                               String cardLast4, Instant paidAt, String checkoutUrl) {
    }

    /**
     * The customer's answer on the sandbox page. For a card: the test card (never stored or logged). For Apple Pay,
     * Tabby and Tamara: approve = true or false, like confirming or cancelling on the real provider's screen.
     * رد العميل في صفحة الدفع التجريبية. للبطاقة: البطاقة التجريبية (ما تنحفظ ولا تنكتب في السجلات). ولـ Apple Pay
     * وتابي وتمارا: approve صح أو غلط، مثل التأكيد أو الإلغاء في شاشة المزوّد الحقيقي.
     */
    public record SandboxRequest(
            @Size(max = 23) String cardNumber,
            @Min(1) @Max(12) Integer expMonth,
            @Min(0) @Max(2100) Integer expYear,
            @Pattern(regexp = "^\\d{3,4}$") String cvc,
            @Size(max = 100) String cardHolder,
            Boolean approve) {

        // Keeps the card number out of any log line that prints the object. / يخلي رقم البطاقة برا أي سجل يطبع الكائن.
        @Override
        public String toString() {
            return "SandboxRequest[****]";
        }
    }
}
