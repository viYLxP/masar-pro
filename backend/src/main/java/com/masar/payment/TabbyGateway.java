package com.masar.payment;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import com.masar.common.ApiException;
import com.masar.common.ErrorCode;
import com.masar.request.ServiceRequest;

/**
 * Tabby "Pay in 4": the customer pays a quarter now and the rest over three months; we receive the full amount.
 * تابي "قسّمها على 4": العميل يدفع الربع الحين والباقي على ثلاث شهور؛ واحنا نستلم المبلغ كامل.
 *
 * <p>Flow (Tabby API v2): create a checkout session; if Tabby approves the customer it returns a web_url to send
 * them to. After they finish, the payment is AUTHORIZED and we capture it, which makes it final (CLOSED).</p>
 * <p>المسار (Tabby API v2): ننشئ جلسة دفع؛ لو تابي وافقت على العميل ترجّع web_url ننقله له. بعد ما يخلّص
 * تصير الدفعة AUTHORIZED ونسوي لها capture، وهذا يخليها نهائية (CLOSED).</p>
 */
public class TabbyGateway implements PaymentGateway {

    private final RestClient client;
    private final String merchantCode;

    public TabbyGateway(RestClient.Builder builder, String apiUrl, String secretKey, String merchantCode) {
        this.client = builder.baseUrl(apiUrl)
                .defaultHeaders(h -> h.setBearerAuth(secretKey))
                .build();
        this.merchantCode = merchantCode;
    }

    @Override
    public Payment.Provider provider() {
        return Payment.Provider.TABBY;
    }

    @Override
    public Checkout start(Payment payment, Urls urls) {
        ServiceRequest r = payment.getRequest();
        String amount = GatewaySupport.decimal(payment.getAmount());
        Map<String, Object> buyer = new LinkedHashMap<>();
        buyer.put("name", r.getCustomerName());
        // Tabby takes the local form "05XXXXXXXX". / تابي تاخذ الصيغة المحلية "05XXXXXXXX".
        buyer.put("phone", "0" + r.getPhone().substring(3));
        if (r.getEmail() != null) {
            buyer.put("email", r.getEmail());
        }
        Map<String, Object> body = Map.of(
                "payment", Map.of(
                        "amount", amount,
                        "currency", payment.getCurrency(),
                        "description", "Masar " + r.getReference(),
                        "buyer", buyer,
                        "shipping_address", Map.of("city", r.getCity().name(),
                                "address", r.getDistrict() + (r.getAddress() == null ? "" : ", " + r.getAddress()),
                                "zip", "00000"),
                        "order", Map.of("reference_id", r.getReference(),
                                "items", List.of(Map.of("title", r.getServiceType().getNameEn(), "quantity", 1,
                                        "unit_price", amount, "category", "Home services")))),
                "lang", "ar",
                "merchant_code", merchantCode,
                "merchant_urls", Map.of("success", urls.successUrl(), "cancel", urls.backUrl(),
                        "failure", urls.failureUrl()));
        Session session = GatewaySupport.call("Tabby", () -> client.post().uri("/api/v2/checkout")
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Session.class));
        String webUrl = session == null ? null : session.webUrl();
        // "rejected" means Tabby will not offer instalments to this customer for this amount.
        // "rejected" تعني إن تابي ما بتعطي هالعميل تقسيط على هالمبلغ.
        if (session == null || !"created".equals(session.status()) || webUrl == null || session.payment() == null) {
            throw new ApiException(ErrorCode.INSTALMENTS_NOT_AVAILABLE);
        }
        return new Checkout(session.payment().id(), webUrl);
    }

    @Override
    public Optional<Result> fetch(Payment payment) {
        TabbyPayment remote = get(payment);
        if (remote == null) {
            return Optional.empty();
        }
        if (remote.amount() != null && !GatewaySupport.same(new BigDecimal(remote.amount()), payment.getAmount())) {
            return Optional.of(new Result(Payment.Status.FAILED, "tabby", null, "AMOUNT_MISMATCH"));
        }
        return switch (remote.status() == null ? "" : remote.status()) {
            case "AUTHORIZED" -> {
                // Capturing makes the payment final; until then Tabby could still release it.
                // الـ capture يخلي الدفعة نهائية؛ وقبلها تابي ممكن تلغيها.
                GatewaySupport.call("Tabby", () -> client.post().uri("/api/v2/payments/{id}/captures",
                                payment.getProviderRef()).contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("amount", GatewaySupport.decimal(payment.getAmount())))
                        .retrieve().toBodilessEntity());
                yield Optional.of(new Result(Payment.Status.PAID, "tabby", null, null));
            }
            case "CLOSED" -> Optional.of(new Result(Payment.Status.PAID, "tabby", null, null));
            case "REJECTED", "EXPIRED" ->
                    Optional.of(new Result(Payment.Status.FAILED, "tabby", null, remote.status()));
            default -> Optional.empty();
        };
    }

    @Override
    public void refund(Payment payment) {
        GatewaySupport.call("Tabby", () -> client.post().uri("/api/v2/payments/{id}/refunds",
                        payment.getProviderRef()).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("amount", GatewaySupport.decimal(payment.getAmount())))
                .retrieve().toBodilessEntity());
    }

    private TabbyPayment get(Payment payment) {
        return GatewaySupport.call("Tabby", () -> client.get().uri("/api/v2/payments/{id}", payment.getProviderRef())
                .retrieve().body(TabbyPayment.class));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Session(String id, String status, TabbyPayment payment, Configuration configuration) {

        String webUrl() {
            if (configuration == null || configuration.available_products() == null
                    || configuration.available_products().installments() == null
                    || configuration.available_products().installments().isEmpty()) {
                return null;
            }
            return configuration.available_products().installments().get(0).web_url();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TabbyPayment(String id, String status, String amount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Configuration(Products available_products) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Products(List<Product> installments) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Product(String web_url) {
    }
}
