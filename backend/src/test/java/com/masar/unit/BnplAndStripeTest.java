package com.masar.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.masar.catalog.ServiceType;
import com.masar.common.ApiException;
import com.masar.common.City;
import com.masar.common.ErrorCode;
import com.masar.payment.Payment;
import com.masar.payment.PaymentGateway;
import com.masar.payment.StripeGateway;
import com.masar.payment.TabbyGateway;
import com.masar.payment.TamaraGateway;
import com.masar.request.ServiceRequest;

/**
 * Stripe, Tabby and Tamara adapters against a mock HTTP server, with request and response shapes from each
 * provider's API docs. They prove our side of the contract; a run against the real sandboxes needs their keys.
 * محوّلات Stripe وتابي وتمارا على خادم HTTP وهمي، بأشكال الطلب والرد من توثيق كل مزوّد. تثبت إن جهتنا من الاتفاق
 * صحيحة؛ والتجربة على بيئاتهم التجريبية الحقيقية تحتاج مفاتيحهم.
 */
class BnplAndStripeTest {

    private static final String API = "https://gateway.test";
    private static final PaymentGateway.Urls URLS =
            new PaymentGateway.Urls("https://site/pay/1", "https://site/pay/1?returned=1", "https://site/pay/1");

    private static Payment payment(Payment.Method method, Payment.Provider provider, String ref) {
        ServiceType service = new ServiceType("AC");
        service.setNameEn("AC maintenance");
        ServiceRequest request = new ServiceRequest("MS-TESTREF1", service);
        request.setCustomerName("Reem Al Qahtani");
        request.setPhone("966551112233");
        request.setEmail("reem@example.com");
        request.setCity(City.RIYADH);
        request.setDistrict("Al Sahafa");
        Payment p = new Payment(request, method, provider, new BigDecimal("345.00"), "customer", Instant.now());
        p.setProviderRef(ref);
        return p;
    }

    @Test
    void stripeCheckoutSessionIsFormEncodedAndApplePayIsRecognised() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        StripeGateway gateway = new StripeGateway(builder, API, "sk_test_x");
        server.expect(requestTo(API + "/v1/checkout/sessions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk_test_x"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "line_items%5B0%5D%5Bprice_data%5D%5Bunit_amount%5D=34500")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "line_items%5B0%5D%5Bprice_data%5D%5Bcurrency%5D=sar")))
                .andRespond(withSuccess("{\"id\":\"cs_test_1\",\"url\":\"https://checkout.stripe.com/c/cs_test_1\"}",
                        MediaType.APPLICATION_JSON));
        PaymentGateway.Checkout checkout = gateway.start(payment(Payment.Method.APPLE_PAY, Payment.Provider.STRIPE,
                null), URLS);
        assertThat(checkout.providerRef()).isEqualTo("cs_test_1");
        server.verify();

        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new StripeGateway(builder, API, "sk_test_x");
        server.expect(requestTo(API + "/v1/checkout/sessions/cs_test_1?expand%5B%5D=payment_intent.latest_charge"))
                .andRespond(withSuccess("""
                        {"id":"cs_test_1","status":"complete","payment_status":"paid","amount_total":34500,
                         "payment_intent":{"id":"pi_1","latest_charge":{"payment_method_details":{"type":"card",
                         "card":{"brand":"visa","last4":"4242","wallet":{"type":"apple_pay"}}}}}}""",
                        MediaType.APPLICATION_JSON));
        PaymentGateway.Result result = gateway.fetch(payment(Payment.Method.APPLE_PAY, Payment.Provider.STRIPE,
                "cs_test_1")).orElseThrow();
        assertThat(result.status()).isEqualTo(Payment.Status.PAID);
        assertThat(result.brand()).isEqualTo("apple_pay");
        assertThat(result.last4()).isEqualTo("4242");
        server.verify();
    }

    @Test
    void tabbyCreatesASessionCapturesAnAuthorisedPaymentAndReportsRejection() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TabbyGateway gateway = new TabbyGateway(builder, API, "sk_test_t", "masar_sa");
        server.expect(requestTo(API + "/api/v2/checkout"))
                .andExpect(header("Authorization", "Bearer sk_test_t"))
                .andExpect(jsonPath("$.merchant_code").value("masar_sa"))
                .andExpect(jsonPath("$.payment.amount").value("345.00"))
                .andExpect(jsonPath("$.payment.currency").value("SAR"))
                .andExpect(jsonPath("$.payment.buyer.phone").value("0551112233"))
                .andExpect(jsonPath("$.merchant_urls.success").value("https://site/pay/1?returned=1"))
                .andRespond(withSuccess("""
                        {"id":"sess_1","status":"created","payment":{"id":"pay_t1"},
                         "configuration":{"available_products":{"installments":[{"web_url":"https://checkout.tabby.sa/x"}]}}}""",
                        MediaType.APPLICATION_JSON));
        PaymentGateway.Checkout checkout = gateway.start(payment(Payment.Method.TABBY, Payment.Provider.TABBY, null),
                URLS);
        assertThat(checkout.providerRef()).isEqualTo("pay_t1");
        assertThat(checkout.url()).isEqualTo("https://checkout.tabby.sa/x");
        server.verify();

        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        TabbyGateway second = new TabbyGateway(builder, API, "sk_test_t", "masar_sa");
        server.expect(requestTo(API + "/api/v2/payments/pay_t1"))
                .andRespond(withSuccess("{\"id\":\"pay_t1\",\"status\":\"AUTHORIZED\",\"amount\":\"345.00\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/api/v2/payments/pay_t1/captures"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.amount").value("345.00"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/api/v2/checkout"))
                .andRespond(withSuccess("{\"id\":\"sess_2\",\"status\":\"rejected\"}", MediaType.APPLICATION_JSON));
        assertThat(second.fetch(payment(Payment.Method.TABBY, Payment.Provider.TABBY, "pay_t1")).orElseThrow()
                .status()).isEqualTo(Payment.Status.PAID);
        assertThatThrownBy(() -> second.start(payment(Payment.Method.TABBY, Payment.Provider.TABBY, null), URLS))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.INSTALMENTS_NOT_AVAILABLE);
        server.verify();
    }

    @Test
    void tamaraCheckoutThenAuthoriseOnApproval() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TamaraGateway gateway = new TamaraGateway(builder, API, "tamara_token", 4);
        server.expect(requestTo(API + "/checkout"))
                .andExpect(header("Authorization", "Bearer tamara_token"))
                .andExpect(jsonPath("$.total_amount.amount").value(345.00))
                .andExpect(jsonPath("$.total_amount.currency").value("SAR"))
                .andExpect(jsonPath("$.country_code").value("SA"))
                .andExpect(jsonPath("$.payment_type").value("PAY_BY_INSTALMENTS"))
                .andExpect(jsonPath("$.instalments").value(4))
                .andExpect(jsonPath("$.consumer.first_name").value("Reem"))
                .andExpect(jsonPath("$.consumer.last_name").value("Al Qahtani"))
                .andExpect(jsonPath("$.consumer.phone_number").value("+966551112233"))
                .andRespond(withSuccess("""
                        {"order_id":"ord_1","checkout_id":"chk_1","checkout_url":"https://checkout-sandbox.tamara.co/x",
                         "status":"new"}""", MediaType.APPLICATION_JSON));
        assertThat(gateway.start(payment(Payment.Method.TAMARA, Payment.Provider.TAMARA, null), URLS).providerRef())
                .isEqualTo("ord_1");
        server.verify();

        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        TamaraGateway later = new TamaraGateway(builder, API, "tamara_token", 4);
        server.expect(requestTo(API + "/merchants/orders/ord_1"))
                .andRespond(withSuccess("{\"order_id\":\"ord_1\",\"status\":\"approved\","
                        + "\"total_amount\":{\"amount\":345.0,\"currency\":\"SAR\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/orders/ord_1/authorise")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"order_id\":\"ord_1\",\"status\":\"authorised\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/merchants/orders/ord_1"))
                .andRespond(withSuccess("{\"order_id\":\"ord_1\",\"status\":\"declined\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/payments/simplified-refund/ord_1"))
                .andExpect(jsonPath("$.total_amount.amount").value(345.00))
                .andRespond(withSuccess("{\"status\":\"fully_refunded\"}", MediaType.APPLICATION_JSON));
        assertThat(later.fetch(payment(Payment.Method.TAMARA, Payment.Provider.TAMARA, "ord_1")).orElseThrow()
                .status()).isEqualTo(Payment.Status.PAID);
        assertThat(later.fetch(payment(Payment.Method.TAMARA, Payment.Provider.TAMARA, "ord_1")).orElseThrow()
                .failureReason()).isEqualTo("DECLINED");
        later.refund(payment(Payment.Method.TAMARA, Payment.Provider.TAMARA, "ord_1"));
        server.verify();
    }
}
