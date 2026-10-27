package com.masar.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masar.catalog.ServiceTypeDtos.ServiceRef;
import com.masar.common.ApiException;
import com.masar.common.BusinessTime;
import com.masar.common.CurrentUser;
import com.masar.common.ErrorCode;
import com.masar.config.AppProperties;
import com.masar.payment.PaymentDtos.CheckoutPage;
import com.masar.payment.PaymentDtos.CheckoutResponse;
import com.masar.payment.PaymentDtos.Instalment;
import com.masar.payment.PaymentDtos.PaymentSummary;
import com.masar.payment.PaymentDtos.PaymentView;
import com.masar.payment.PaymentDtos.SandboxRequest;
import com.masar.request.PaymentStatus;
import com.masar.request.RequestEvent;
import com.masar.request.RequestStatus;
import com.masar.request.ServiceRequest;

/**
 * Payment rules: when a request can be paid, online checkout (card, Apple Pay, Tabby, Tamara), cash on the visit,
 * and refunds on cancellation.
 * قواعد الدفع: متى ينفع يندفع الطلب، والدفع أونلاين (بطاقة، Apple Pay، تابي، تمارا)، والكاش وقت الزيارة،
 * والاسترجاع عند الإلغاء.
 *
 * <p>The amount is always the quote total read from the database, never a number sent by the browser.</p>
 * <p>المبلغ دايم هو إجمالي العرض من قاعدة البيانات، أبدًا رقم جاي من المتصفح.</p>
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    // Online payment is open once the customer accepted the quote, and stays open until the job is paid.
    // الدفع أونلاين يفتح من لما العميل يوافق على العرض، ويبقى مفتوح لين ينْدفع الشغل.
    private static final Set<RequestStatus> ONLINE = EnumSet.of(RequestStatus.ACCEPTED, RequestStatus.SCHEDULED,
            RequestStatus.IN_PROGRESS, RequestStatus.COMPLETED);
    // Cash is handed to the technician, so only once a visit is booked. / الكاش ينعطى للفني، فبس بعد ما تنحجز الزيارة.
    private static final Set<RequestStatus> CASH = EnumSet.of(RequestStatus.SCHEDULED, RequestStatus.IN_PROGRESS,
            RequestStatus.COMPLETED);
    // Gateways refuse tiny charges (Moyasar's minimum is 1 SAR). / البوابات ترفض المبالغ الصغيرة جدًا (أقل شي عند Moyasar ريال).
    private static final BigDecimal MIN_ONLINE = BigDecimal.ONE;

    private final PaymentRepository payments;
    private final PaymentGateways gateways;
    private final BusinessTime time;
    private final String publicUrl;
    private final AppProperties.Instalments instalments;

    public PaymentService(PaymentRepository payments, PaymentGateways gateways, BusinessTime time,
                          AppProperties properties) {
        this.payments = payments;
        this.gateways = gateways;
        this.time = time;
        this.instalments = properties.payments().instalments();
        // Without the trailing slash, so links never get "//". / بدون الشرطة الأخيرة، حتى الروابط ما يصير فيها "//".
        this.publicUrl = properties.payments().publicUrl().replaceAll("/+$", "");
    }

    /**
     * Opens a checkout with the chosen method. Reuses a still-open attempt of the same method, so pressing "Pay"
     * twice does not create two invoices the customer could both pay.
     * يفتح الدفع بالطريقة المختارة. يعيد استخدام المحاولة المفتوحة لنفس الطريقة، حتى الضغط على "ادفع" مرتين ما
     * يسوي فاتورتين ممكن العميل يدفعهم الثنتين.
     */
    @Transactional
    public CheckoutResponse startOnline(ServiceRequest request, Payment.Method method) {
        requireOnlinePayable(request);
        if (!PaymentGateways.ONLINE.contains(method)) {
            throw fieldError("method", "Online");
        }
        BigDecimal amount = request.getQuote().getTotal();
        if (method.isInstalments() && !instalmentsAllowed(amount)) {
            throw new ApiException(ErrorCode.INSTALMENTS_NOT_AVAILABLE,
                    Map.of("min", instalments.minAmount(), "max", instalments.maxAmount()));
        }
        PaymentGateway gateway = gateways.forMethod(method);
        for (Payment open : payments.findByRequestAndStatus(request, Payment.Status.PENDING)) {
            refresh(open);
            if (request.getPaymentStatus() != PaymentStatus.UNPAID) {
                // It turned out the customer had already paid with this attempt. / طلع إن العميل دفع بهالمحاولة.
                throw new ApiException(ErrorCode.PAYMENT_NOT_ALLOWED);
            }
            if (open.getStatus() != Payment.Status.PENDING || open.getMethod() != method) {
                continue;
            }
            if (open.getProvider() == gateway.provider() && open.getAmount().compareTo(amount) == 0) {
                return new CheckoutResponse(open.getId(), open.getCheckoutUrl());
            }
            // The price or the gateway changed since: close the old attempt. / السعر أو البوابة تغيّرت: نقفل المحاولة القديمة.
            open.markFailed("SUPERSEDED", null, null);
        }
        Payment payment = new Payment(request, method, gateway.provider(), amount, RequestEvent.CUSTOMER, time.now());
        PaymentGateway.Checkout checkout = gateway.start(payment, urls(payment));
        payment.setProviderRef(checkout.providerRef());
        payment.setCheckoutUrl(checkout.url());
        payments.save(payment);
        return new CheckoutResponse(payment.getId(), checkout.url());
    }

    /**
     * The payment page. For a real gateway it first asks the gateway for the latest status, so a customer coming
     * back from Moyasar sees "paid" at once.
     * صفحة الدفع. للبوابة الحقيقية تسأل البوابة أول عن آخر حالة، حتى العميل الراجع من Moyasar يشوف "مدفوع" على طول.
     */
    @Transactional
    public CheckoutPage page(UUID id) {
        Payment payment = find(id);
        refresh(payment);
        return page(payment);
    }

    /**
     * The customer's answer on the sandbox page: a test card, or approving / cancelling Apple Pay, Tabby or Tamara.
     * A declined card leaves the attempt open for another card.
     * رد العميل في صفحة الدفع التجريبية: بطاقة تجريبية، أو الموافقة / الإلغاء في Apple Pay أو تابي أو تمارا.
     * البطاقة المرفوضة تخلي المحاولة مفتوحة لبطاقة ثانية.
     */
    @Transactional
    public CheckoutPage payInSandbox(UUID id, SandboxRequest answer) {
        Payment payment = find(id);
        if (payment.getProvider() != Payment.Provider.SANDBOX) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_ALLOWED);
        }
        if (payment.getStatus() == Payment.Status.PAID) {
            return page(payment);
        }
        if (payment.getStatus() == Payment.Status.REFUNDED || "SUPERSEDED".equals(payment.getFailureReason())) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_ALLOWED);
        }
        requireOnlinePayable(payment.getRequest());
        if (payment.getMethod() == Payment.Method.CARD) {
            chargeTestCard(payment, answer);
        } else if (answer.approve() == null) {
            throw fieldError("approve", "NotNull");
        } else if (answer.approve()) {
            settle(payment, payment.getMethod().name().toLowerCase(), null);
        } else {
            payment.markFailed("CANCELLED_BY_CUSTOMER", null, null);
        }
        return page(payment);
    }

    private void chargeTestCard(Payment payment, SandboxRequest card) {
        Map<String, String> missing = new java.util.LinkedHashMap<>();
        if (card.cardNumber() == null || card.cardNumber().isBlank()) {
            missing.put("cardNumber", "NotBlank");
        }
        if (card.expMonth() == null) {
            missing.put("expMonth", "NotNull");
        }
        if (card.expYear() == null) {
            missing.put("expYear", "NotNull");
        }
        if (card.cvc() == null || card.cvc().isBlank()) {
            missing.put("cvc", "NotBlank");
        }
        if (card.cardHolder() == null || card.cardHolder().isBlank()) {
            missing.put("cardHolder", "NotBlank");
        }
        if (!missing.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("fields", missing));
        }
        String digits = SandboxCards.clean(card.cardNumber());
        if (digits == null) {
            throw fieldError("cardNumber", "CardNumber");
        }
        if (!SandboxCards.expiryValid(card.expMonth(), card.expYear(), YearMonth.now(time.zone()))) {
            throw fieldError("expYear", "CardExpired");
        }
        SandboxCards.Outcome outcome = SandboxCards.charge(digits);
        if (outcome.approved()) {
            settle(payment, outcome.brand(), outcome.last4());
        } else {
            payment.markFailed(outcome.failureReason(), outcome.brand(), outcome.last4());
        }
    }

    /**
     * Records cash the technician collected. The office uses it too, e.g. for a bank transfer it confirmed.
     * يسجّل الكاش اللي استلمه الفني. والمكتب يستخدمه كمان، مثلًا لتحويل بنكي تأكد منه.
     */
    @Transactional
    public void recordCash(ServiceRequest request) {
        if (!CASH.contains(request.getStatus()) || request.getPaymentStatus() != PaymentStatus.UNPAID
                || request.getQuote() == null || request.getQuote().getTotal().signum() <= 0) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_ALLOWED);
        }
        Payment payment = new Payment(request, Payment.Method.CASH, Payment.Provider.CASH,
                request.getQuote().getTotal(), CurrentUser.username(), time.now());
        payment.markPaid(time.now(), null, null);
        request.setPaymentStatus(PaymentStatus.PAID);
        // An open card attempt can no longer be used. / محاولة البطاقة المفتوحة ما عاد تنفع.
        payments.findByRequestAndStatus(request, Payment.Status.PENDING)
                .forEach(p -> p.markFailed("SUPERSEDED", null, null));
        payments.save(payment);
    }

    /**
     * Called when the office cancels a request: returns the money of a paid request.
     * ينادى لما المكتب يلغي طلب: يرجّع فلوس الطلب المدفوع.
     */
    @Transactional
    public void refundIfPaid(ServiceRequest request) {
        if (request.getPaymentStatus() != PaymentStatus.PAID) {
            payments.findByRequestAndStatus(request, Payment.Status.PENDING)
                    .forEach(p -> p.markFailed("SUPERSEDED", null, null));
            return;
        }
        Payment paid = payments.findFirstByRequestAndStatus(request, Payment.Status.PAID)
                .orElseThrow(() -> new IllegalStateException("PAID request without a paid payment"));
        if (paid.getMethod() != Payment.Method.CASH) {
            // The gateway is called first: if it fails, the cancellation is rolled back with it.
            // نتصل بالبوابة أول: لو فشلت، الإلغاء يتراجع معها.
            gateways.of(paid.getProvider()).refund(paid);
        }
        paid.markRefunded(time.now());
        request.setPaymentStatus(PaymentStatus.REFUNDED);
    }

    @Transactional(readOnly = true)
    public List<PaymentView> history(ServiceRequest request) {
        return payments.findByRequestOrderByCreatedAtAsc(request).stream().map(PaymentView::of).toList();
    }

    @Transactional(readOnly = true)
    public PaymentSummary summary(ServiceRequest request) {
        Payment settled = request.getPaymentStatus() == PaymentStatus.UNPAID ? null
                : payments.findByRequestOrderByCreatedAtAsc(request).stream()
                .filter(p -> p.getStatus() == Payment.Status.PAID || p.getStatus() == Payment.Status.REFUNDED)
                .reduce((a, b) -> b).orElse(null);
        BigDecimal due = request.getQuote() == null ? null : request.getQuote().getTotal();
        boolean payable = isOnlinePayable(request);
        List<Payment.Method> methods = !payable ? List.of() : PaymentGateways.ONLINE.stream()
                .filter(m -> !m.isInstalments() || instalmentsAllowed(due)).toList();
        return new PaymentSummary(request.getPaymentStatus(), payable, due, methods,
                payable && instalmentsAllowed(due) ? plan(due) : List.of(),
                settled == null ? null : settled.getMethod(),
                settled == null ? null : settled.getCardBrand(),
                settled == null ? null : settled.getCardLast4(),
                settled == null ? null : settled.getPaidAt());
    }

    public boolean isOnlinePayable(ServiceRequest request) {
        return ONLINE.contains(request.getStatus()) && request.getPaymentStatus() == PaymentStatus.UNPAID
                && request.getQuote() != null && request.getQuote().getTotal().compareTo(MIN_ONLINE) >= 0;
    }

    private boolean instalmentsAllowed(BigDecimal amount) {
        return amount != null && amount.compareTo(instalments.minAmount()) >= 0
                && amount.compareTo(instalments.maxAmount()) <= 0;
    }

    /**
     * Equal monthly instalments, the first one today. Any halala left over from the division goes on the first,
     * so the instalments always add up to the exact total.
     * دفعات شهرية متساوية، أولها اليوم. الهللة الزايدة من القسمة تنضاف على الأولى، فمجموع الدفعات يساوي الإجمالي بالضبط.
     */
    List<Instalment> plan(BigDecimal total) {
        int count = instalments.count();
        BigDecimal each = total.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
        BigDecimal first = total.subtract(each.multiply(BigDecimal.valueOf(count - 1)));
        LocalDate today = time.today();
        List<Instalment> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new Instalment(i + 1, i == 0 ? first : each, today.plusMonths(i)));
        }
        return list;
    }

    private void requireOnlinePayable(ServiceRequest request) {
        if (!isOnlinePayable(request)) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_ALLOWED);
        }
    }

    // Asks a real gateway about an open attempt and applies the answer. / يسأل البوابة الحقيقية عن محاولة مفتوحة ويطبّق الرد.
    private void refresh(Payment payment) {
        if (payment.getStatus() != Payment.Status.PENDING || payment.getProvider() == Payment.Provider.SANDBOX) {
            return;
        }
        gateways.of(payment.getProvider()).fetch(payment).ifPresent(result -> {
            if (result.status() == Payment.Status.PAID) {
                settle(payment, result.brand(), result.last4());
            } else if (result.status() == Payment.Status.FAILED) {
                payment.markFailed(result.failureReason(), result.brand(), result.last4());
            }
        });
    }

    /**
     * Marks the payment and the request as paid. If the request was already paid another way (cash, or a second
     * invoice), this money is returned at once instead of charging the customer twice.
     * يعلّم الدفعة والطلب كمدفوعين. لو الطلب انْدفع قبل بطريقة ثانية (كاش، أو فاتورة ثانية)، نرجّع هالمبلغ فورًا
     * بدل ما ينخصم العميل مرتين.
     */
    private void settle(Payment payment, String brand, String last4) {
        ServiceRequest request = payment.getRequest();
        if (request.getPaymentStatus() != PaymentStatus.UNPAID) {
            log.warn("Duplicate payment {} for request {}: refunding", payment.getId(), request.getReference());
            gateways.of(payment.getProvider()).refund(payment);
            payment.markFailed("DUPLICATE_REFUNDED", brand, last4);
            return;
        }
        payment.markPaid(time.now(), brand, last4);
        request.setPaymentStatus(PaymentStatus.PAID);
    }

    private CheckoutPage page(Payment p) {
        ServiceRequest r = p.getRequest();
        return new CheckoutPage(p.getId(), p.getMethod(), p.getStatus(), p.getProvider(), p.getAmount(),
                p.getCurrency(), r.getReference(), ServiceRef.of(r.getServiceType()),
                p.getMethod().isInstalments() ? plan(p.getAmount()) : List.of(), p.getFailureReason(),
                p.getCardBrand(), p.getCardLast4(), p.getPaidAt(), p.getCheckoutUrl());
    }

    private PaymentGateway.Urls urls(Payment payment) {
        String page = publicUrl + "/pay/" + payment.getId();
        return new PaymentGateway.Urls(page, page + "?returned=1", page);
    }

    private Payment find(UUID id) {
        return payments.findWithRequest(id).orElseThrow(() -> ApiException.notFound("payment", id));
    }

    private static ApiException fieldError(String field, String rule) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of(field, rule)));
    }
}
