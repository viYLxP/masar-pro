package com.masar.payment;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import com.masar.common.ApiException;
import com.masar.common.ErrorCode;

/**
 * Moyasar (Saudi gateway: mada, Visa, Mastercard, Apple Pay, STC Pay) through its hosted invoice page.
 * Moyasar (بوابة سعودية: مدى وفيزا وماستركارد وApple Pay وSTC Pay) عن طريق صفحة الفاتورة المستضافة عندهم.
 *
 * <p>Why invoices: the customer types the card on Moyasar's page, so card data never reaches our servers
 * (less PCI scope), and we do not need their JavaScript in the Angular app. After paying, Moyasar redirects to our
 * return page, which asks the API to fetch the invoice: the status is read from Moyasar with our secret key, never
 * trusted from the browser's URL.</p>
 * <p>ليش الفواتير: العميل يكتب بطاقته في صفحة Moyasar، فبيانات البطاقة ما توصل لخوادمنا أبدًا (نطاق PCI أقل)،
 * وما نحتاج سكربتهم داخل Angular. بعد الدفع Moyasar يرجّع العميل لصفحة الرجوع حقتنا، واللي تطلب من الـ API يجيب
 * الفاتورة: الحالة تنقرأ من Moyasar بالمفتاح السري، ما نصدّق رابط المتصفح.</p>
 *
 * <p>API: Basic auth with the secret key as the username and an empty password; amounts in halalas (100 = 1 SAR).</p>
 * <p>الـ API: مصادقة Basic بالمفتاح السري كاسم مستخدم وكلمة مرور فاضية؛ والمبالغ بالهللة (100 = ريال).</p>
 */
public class MoyasarGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MoyasarGateway.class);

    private final RestClient client;

    public MoyasarGateway(RestClient.Builder builder, String apiUrl, String secretKey) {
        this.client = builder.baseUrl(apiUrl)
                .defaultHeaders(h -> h.setBasicAuth(secretKey, ""))
                .build();
    }

    @Override
    public Payment.Provider provider() {
        return Payment.Provider.MOYASAR;
    }

    @Override
    public Checkout start(Payment payment, Urls urls) {
        Map<String, Object> body = Map.of(
                "amount", halalas(payment.getAmount()),
                "currency", payment.getCurrency(),
                "description", "Masar " + payment.getRequest().getReference(),
                "success_url", urls.successUrl(),
                "back_url", urls.backUrl(),
                // Lets the office find the payment from Moyasar's dashboard. / يخلّي المكتب يلقى الدفعة من لوحة Moyasar.
                "metadata", Map.of("payment_id", payment.getId().toString(),
                        "reference", payment.getRequest().getReference()));
        Invoice invoice = call(() -> client.post().uri("/invoices").contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(Invoice.class));
        if (invoice == null || invoice.id() == null || invoice.url() == null) {
            throw new ApiException(ErrorCode.PAYMENT_PROVIDER_ERROR);
        }
        return new Checkout(invoice.id(), invoice.url());
    }

    @Override
    public Optional<Result> fetch(Payment payment) {
        Invoice invoice = call(() -> client.get().uri("/invoices/{id}", payment.getProviderRef())
                .retrieve().body(Invoice.class));
        if (invoice == null) {
            return Optional.empty();
        }
        // The amount must match ours, so a tampered or reused invoice cannot mark the request as paid.
        // المبلغ لازم يطابق مبلغنا، حتى فاتورة معدّلة أو مستخدمة قبل ما تعلّم الطلب كمدفوع.
        if (invoice.amount() != null && invoice.amount() != halalas(payment.getAmount())) {
            log.warn("Moyasar invoice {} amount {} does not match payment {}", invoice.id(), invoice.amount(),
                    payment.getId());
            return Optional.of(new Result(Payment.Status.FAILED, null, null, "AMOUNT_MISMATCH"));
        }
        InvoicePayment paid = invoice.payments() == null ? null : invoice.payments().stream()
                .filter(p -> "paid".equals(p.status()) || "captured".equals(p.status()))
                .findFirst().orElse(null);
        return switch (invoice.status() == null ? "" : invoice.status()) {
            case "paid" -> Optional.of(new Result(Payment.Status.PAID, paid == null ? null : paid.brandName(),
                    paid == null ? null : paid.last4(), null));
            case "expired", "canceled", "voided", "failed" ->
                    Optional.of(new Result(Payment.Status.FAILED, null, null, invoice.status().toUpperCase()));
            // initiated / on_hold: the customer has not finished yet. / initiated أو on_hold: العميل لسا ما خلّص.
            default -> Optional.empty();
        };
    }

    @Override
    public void refund(Payment payment) {
        Invoice invoice = call(() -> client.get().uri("/invoices/{id}", payment.getProviderRef())
                .retrieve().body(Invoice.class));
        if (invoice == null || invoice.payments() == null) {
            throw new ApiException(ErrorCode.PAYMENT_PROVIDER_ERROR);
        }
        for (InvoicePayment p : invoice.payments()) {
            if ("paid".equals(p.status()) || "captured".equals(p.status())) {
                call(() -> client.post().uri("/payments/{id}/refund", p.id()).retrieve().toBodilessEntity());
            }
        }
    }

    private static <T> T call(java.util.function.Supplier<T> request) {
        return GatewaySupport.call("Moyasar", request);
    }

    private static int halalas(BigDecimal amount) {
        return GatewaySupport.halalas(amount);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Invoice(String id, String status, Integer amount, String currency, String url,
                   List<InvoicePayment> payments) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record InvoicePayment(String id, String status, Source source) {

        // An Apple Pay payment is shown as "apple_pay" rather than the card behind it.
        // دفعة Apple Pay تبين "apple_pay" بدل البطاقة اللي وراها.
        String brandName() {
            if (source == null) {
                return null;
            }
            return "applepay".equals(source.type()) ? "apple_pay" : source.company();
        }

        // Moyasar masks the number like "4111-11XX-XXXX-1111". / Moyasar يخفي الرقم بشكل "4111-11XX-XXXX-1111".
        String last4() {
            if (source == null || source.number() == null) {
                return null;
            }
            String digits = source.number().replaceAll("\\D", "");
            return digits.length() >= 4 ? digits.substring(digits.length() - 4) : null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Source(String type, String company, @JsonProperty("number") String number) {
    }
}
