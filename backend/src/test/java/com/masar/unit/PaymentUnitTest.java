package com.masar.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.masar.catalog.ServiceType;
import com.masar.common.ApiException;
import com.masar.config.AppProperties;
import com.masar.payment.MoyasarGateway;
import com.masar.payment.Payment;
import com.masar.payment.PaymentGateway;
import com.masar.payment.PaymentGateways;
import com.masar.payment.SandboxCards;
import com.masar.payment.SandboxGateway;
import com.masar.request.ServiceRequest;

/**
 * The gateway adapters without a database. Moyasar is tested against a mock HTTP server with the request and
 * response shapes from its API docs; a real call needs a test key (see README).
 * محوّلات البوابات بدون قاعدة بيانات. Moyasar ينختبر على خادم HTTP وهمي بأشكال الطلب والرد من توثيق الـ API حقهم؛
 * والاتصال الحقيقي يحتاج مفتاح تجريبي (شوف README).
 */
class PaymentUnitTest {

    private static final String API = "https://api.moyasar.test/v1";

    private static Payment payment(String amount) {
        ServiceRequest request = new ServiceRequest("MS-TESTREF1", new ServiceType("AC"));
        return new Payment(request, Payment.Method.CARD, Payment.Provider.MOYASAR, new BigDecimal(amount), "customer",
                Instant.now());
    }

    @Test
    void sandboxCards() {
        assertThat(SandboxCards.clean("4111 1111 1111 1111")).isEqualTo("4111111111111111");
        assertThat(SandboxCards.clean("4111-1111-1111-1112")).isNull();
        assertThat(SandboxCards.clean("1234")).isNull();
        assertThat(SandboxCards.charge("4111111111111111").approved()).isTrue();
        assertThat(SandboxCards.charge("4111111111111111").brand()).isEqualTo("visa");
        assertThat(SandboxCards.charge("5105105105105100").brand()).isEqualTo("mastercard");
        assertThat(SandboxCards.charge("5888450000000003").brand()).isEqualTo("mada");
        assertThat(SandboxCards.charge(SandboxCards.DECLINED).failureReason()).isEqualTo("CARD_DECLINED");
        YearMonth now = YearMonth.of(2026, 10);
        assertThat(SandboxCards.expiryValid(10, 26, now)).isTrue();
        assertThat(SandboxCards.expiryValid(9, 2026, now)).isFalse();
        assertThat(SandboxCards.expiryValid(13, 2030, now)).isFalse();
    }

    @Test
    void moyasarCreatesAnInvoiceInHalalasWithBasicAuth() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MoyasarGateway gateway = new MoyasarGateway(builder, API, "sk_test_abc");
        String basic = "Basic " + Base64.getEncoder().encodeToString("sk_test_abc:".getBytes(StandardCharsets.UTF_8));
        server.expect(requestTo(API + "/invoices"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", basic))
                .andExpect(jsonPath("$.amount").value(34500))
                .andExpect(jsonPath("$.currency").value("SAR"))
                .andExpect(jsonPath("$.success_url").value("https://site/pay/1?returned=1"))
                .andExpect(jsonPath("$.metadata.reference").value("MS-TESTREF1"))
                .andRespond(withSuccess("""
                        {"id":"inv_1","status":"initiated","amount":34500,"currency":"SAR",
                         "url":"https://checkout.moyasar.com/invoices/inv_1","payments":[]}""",
                        MediaType.APPLICATION_JSON));
        PaymentGateway.Checkout checkout = gateway.start(payment("345.00"),
                new PaymentGateway.Urls("https://site/pay/1", "https://site/pay/1?returned=1", "https://site/pay/1"));
        assertThat(checkout.providerRef()).isEqualTo("inv_1");
        assertThat(checkout.url()).isEqualTo("https://checkout.moyasar.com/invoices/inv_1");
        server.verify();
    }

    @Test
    void moyasarStatusIsReadFromTheInvoiceAndTheAmountMustMatch() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MoyasarGateway gateway = new MoyasarGateway(builder, API, "sk_test_abc");
        Payment p = payment("345.00");
        p.setProviderRef("inv_1");
        server.expect(requestTo(API + "/invoices/inv_1")).andRespond(withSuccess("""
                {"id":"inv_1","status":"paid","amount":34500,"payments":[{"id":"pay_9","status":"paid",
                 "source":{"type":"creditcard","company":"mada","number":"4406-47XX-XXXX-0007"}}]}""",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/invoices/inv_1")).andRespond(withSuccess("""
                {"id":"inv_1","status":"paid","amount":100,"payments":[]}""", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/invoices/inv_1")).andRespond(withSuccess("""
                {"id":"inv_1","status":"initiated","amount":34500,"payments":[]}""", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/invoices/inv_1")).andRespond(withServerError());

        PaymentGateway.Result paid = gateway.fetch(p).orElseThrow();
        assertThat(paid.status()).isEqualTo(Payment.Status.PAID);
        assertThat(paid.brand()).isEqualTo("mada");
        assertThat(paid.last4()).isEqualTo("0007");
        assertThat(gateway.fetch(p).orElseThrow().failureReason()).isEqualTo("AMOUNT_MISMATCH");
        assertThat(gateway.fetch(p)).isEmpty();
        assertThatThrownBy(() -> gateway.fetch(p)).isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", com.masar.common.ErrorCode.PAYMENT_PROVIDER_ERROR);
        server.verify();
    }

    @Test
    void moyasarRefundsThePaidPaymentOfTheInvoice() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MoyasarGateway gateway = new MoyasarGateway(builder, API, "sk_test_abc");
        Payment p = payment("345.00");
        p.setProviderRef("inv_1");
        server.expect(requestTo(API + "/invoices/inv_1")).andRespond(withSuccess("""
                {"id":"inv_1","status":"paid","amount":34500,"payments":[
                 {"id":"pay_1","status":"failed"},{"id":"pay_2","status":"paid"}]}""", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/payments/pay_2/refund")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        gateway.refund(p);
        server.verify();
    }

    @Test
    void choosingMoyasarWithoutAKeyStopsTheAppFromStarting() {
        assertThatThrownBy(() -> new PaymentGateways(properties(Payment.Provider.MOYASAR, ""), new SandboxGateway()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MOYASAR_SECRET_KEY");
        assertThat(new PaymentGateways(properties(Payment.Provider.STRIPE, "sk_test_1"), new SandboxGateway())
                .forMethod(Payment.Method.APPLE_PAY).provider()).isEqualTo(Payment.Provider.STRIPE);
        // Without Tabby/Tamara keys, instalments run in the sandbox. / بدون مفاتيح تابي وتمارا، التقسيط يشتغل تجريبي.
        assertThat(new PaymentGateways(properties(Payment.Provider.STRIPE, "sk_test_1"), new SandboxGateway())
                .forMethod(Payment.Method.TABBY).provider()).isEqualTo(Payment.Provider.SANDBOX);
        assertThat(new PaymentGateways(properties(Payment.Provider.MOYASAR, "sk_test_1"), new SandboxGateway())
                .forMethod(Payment.Method.CARD).provider()).isEqualTo(Payment.Provider.MOYASAR);
        assertThat(new PaymentGateways(properties(Payment.Provider.SANDBOX, ""), new SandboxGateway())
                .forMethod(Payment.Method.CARD).provider()).isEqualTo(Payment.Provider.SANDBOX);
    }

    private static AppProperties properties(Payment.Provider provider, String key) {
        return new AppProperties(new AppProperties.Jwt("x".repeat(32), Duration.ofHours(1)),
                new AppProperties.Cors(List.of()), ZoneId.of("Asia/Riyadh"),
                new AppProperties.Seed(false, null, null, null),
                new AppProperties.RateLimit(Duration.ofMinutes(10), 5, 30),
                new AppProperties.Payments(provider, "http://localhost", new AppProperties.Moyasar(API, key),
                        new AppProperties.Stripe(API, key), new AppProperties.Tabby(API, "", ""),
                        new AppProperties.Tamara(API, ""),
                        new AppProperties.Instalments(new BigDecimal("100"), new BigDecimal("5000"), 4)));
    }
}
