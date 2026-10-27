package com.masar.payment;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.masar.common.ApiException;
import com.masar.common.ErrorCode;

/**
 * Stripe Checkout: Stripe's hosted page takes the card or Apple Pay (shown automatically on Apple devices).
 * Stripe Checkout: صفحة Stripe المستضافة تاخذ البطاقة أو Apple Pay (يظهر تلقائيًا في أجهزة Apple).
 *
 * <p>Stripe's API takes form-encoded bodies and a Bearer secret key; amounts are in the smallest unit.
 * The result is read back from the session with our key, never trusted from the redirect URL.</p>
 * <p>الـ API حق Stripe ياخذ بيانات بصيغة form ومفتاح سري Bearer؛ والمبالغ بأصغر وحدة.
 * والنتيجة تنقرأ من الجلسة بمفتاحنا، ما نصدّق رابط الرجوع.</p>
 */
public class StripeGateway implements PaymentGateway {

    private final RestClient client;

    public StripeGateway(RestClient.Builder builder, String apiUrl, String secretKey) {
        this.client = builder.baseUrl(apiUrl)
                .defaultHeaders(h -> h.setBearerAuth(secretKey))
                .build();
    }

    @Override
    public Payment.Provider provider() {
        return Payment.Provider.STRIPE;
    }

    @Override
    public Checkout start(Payment payment, Urls urls) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "payment");
        form.add("success_url", urls.successUrl());
        form.add("cancel_url", urls.backUrl());
        form.add("client_reference_id", payment.getId().toString());
        form.add("metadata[payment_id]", payment.getId().toString());
        form.add("metadata[reference]", payment.getRequest().getReference());
        form.add("line_items[0][quantity]", "1");
        form.add("line_items[0][price_data][currency]", payment.getCurrency().toLowerCase());
        form.add("line_items[0][price_data][unit_amount]",
                Integer.toString(GatewaySupport.halalas(payment.getAmount())));
        form.add("line_items[0][price_data][product_data][name]", "Masar " + payment.getRequest().getReference());
        Session session = GatewaySupport.call("Stripe", () -> client.post().uri("/v1/checkout/sessions")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Session.class));
        if (session == null || session.id() == null || session.url() == null) {
            throw new ApiException(ErrorCode.PAYMENT_PROVIDER_ERROR);
        }
        return new Checkout(session.id(), session.url());
    }

    @Override
    public Optional<Result> fetch(Payment payment) {
        Session session = GatewaySupport.call("Stripe", () -> client.get()
                .uri("/v1/checkout/sessions/{id}?expand[]=payment_intent.latest_charge", payment.getProviderRef())
                .retrieve().body(Session.class));
        if (session == null) {
            return Optional.empty();
        }
        if (session.amountTotal() != null && session.amountTotal() != GatewaySupport.halalas(payment.getAmount())) {
            return Optional.of(new Result(Payment.Status.FAILED, null, null, "AMOUNT_MISMATCH"));
        }
        if ("paid".equals(session.paymentStatus())) {
            Card card = session.card();
            String brand = card == null ? null
                    : card.wallet() != null && card.wallet().type() != null ? card.wallet().type() : card.brand();
            return Optional.of(new Result(Payment.Status.PAID, brand, card == null ? null : card.last4(), null));
        }
        if ("expired".equals(session.status())) {
            return Optional.of(new Result(Payment.Status.FAILED, null, null, "EXPIRED"));
        }
        return Optional.empty();
    }

    @Override
    public void refund(Payment payment) {
        Session session = GatewaySupport.call("Stripe", () -> client.get()
                .uri("/v1/checkout/sessions/{id}?expand[]=payment_intent", payment.getProviderRef())
                .retrieve().body(Session.class));
        if (session == null || session.paymentIntent() == null || session.paymentIntent().id() == null) {
            throw new ApiException(ErrorCode.PAYMENT_PROVIDER_ERROR);
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("payment_intent", session.paymentIntent().id());
        GatewaySupport.call("Stripe", () -> client.post().uri("/v1/refunds")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().toBodilessEntity());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Session(String id, String url, String status, @JsonProperty("payment_status") String paymentStatus,
                   @JsonProperty("amount_total") Integer amountTotal,
                   @JsonProperty("payment_intent") Intent paymentIntent) {

        Card card() {
            if (paymentIntent == null || paymentIntent.latestCharge() == null
                    || paymentIntent.latestCharge().details() == null) {
                return null;
            }
            return paymentIntent.latestCharge().details().card();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Intent(String id, @JsonProperty("latest_charge") Charge latestCharge) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Charge(@JsonProperty("payment_method_details") Details details) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Details(String type, Card card) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Card(String brand, String last4, Wallet wallet) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Wallet(String type) {
    }
}
