package com.masar.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;

import com.masar.support.IntegrationTest;

/**
 * Paying for a request: online with the sandbox test cards, cash to the technician, and refunds on cancellation.
 * دفع الطلب: أونلاين ببطاقات الوضع التجريبي، وكاش للفني، والاسترجاع عند الإلغاء.
 */
class PaymentTest extends IntegrationTest {

    private static final String PHONE = "0551234567";
    private static final String GOOD_CARD = """
            {"cardNumber":"4111 1111 1111 1111","expMonth":12,"expYear":%d,"cvc":"123","cardHolder":"Test"}""";

    private String goodCard() {
        return GOOD_CARD.formatted(time.today().getYear() + 2);
    }

    private String card(String number) {
        return goodCard().replace("4111 1111 1111 1111", number);
    }

    private String startPayment(String ref) throws Exception {
        return startPayment(ref, "CARD");
    }

    private String startPayment(String ref, String method) throws Exception {
        String body = mvc.perform(json(post("/api/public/requests/" + ref + "/pay"))
                        .content(payBody(PHONE, method)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.checkoutUrl");
        String id = JsonPath.read(body, "$.paymentId");
        // The sandbox page lives in our own app. / صفحة الدفع التجريبية داخل تطبيقنا.
        assertThat(url).endsWith("/pay/" + id);
        return id;
    }

    private static String payBody(String phone, String method) {
        return "{\"phone\":\"%s\",\"method\":\"%s\"}".formatted(phone, method);
    }

    private String referenceOf(long id) {
        return jdbc.queryForObject("select reference from service_request where id = ?", String.class, id);
    }

    @Test
    void paymentOpensOnlyAfterTheQuoteIsAccepted() throws Exception {
        String ref = book(acId, "RIYADH", PHONE);
        long id = requestId(ref);
        quoteAndSend(id, dispatcherToken(), "300.00");
        mvc.perform(json(post("/api/public/requests/" + ref + "/pay")).content(payBody(PHONE, "CARD")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_ALLOWED"));
        mvc.perform(json(post("/api/public/requests/track")).content("""
                        {"reference":"%s","phone":"%s"}""".formatted(ref, PHONE)))
                .andExpect(jsonPath("$.payment.payable").value(false))
                .andExpect(jsonPath("$.payment.status").value("UNPAID"));
        // The wrong phone cannot open a payment. / الجوال الغلط ما يفتح دفع.
        accept(ref, PHONE);
        mvc.perform(json(post("/api/public/requests/" + ref + "/pay")).content(payBody("0559999999", "CARD")))
                .andExpect(status().isNotFound());
        // Cash is not an online method. / الكاش مو طريقة أونلاين.
        mvc.perform(json(post("/api/public/requests/" + ref + "/pay")).content(payBody(PHONE, "CASH")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.method").value("Online"));
    }

    @Test
    void aGuestPaysWithATestCardAfterADeclinedOne() throws Exception {
        long id = acceptedAcRequest(dispatcherToken(), PHONE);
        String paymentId = startPayment(referenceOf(id));

        // The amount is the quote total with VAT, and the page shows no phone or address.
        // المبلغ إجمالي العرض مع الضريبة، والصفحة ما تعرض جوال ولا عنوان.
        mvc.perform(get("/api/public/payments/" + paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(345.00))
                .andExpect(jsonPath("$.currency").value("SAR"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.provider").value("SANDBOX"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("966551234567"))));

        mvc.perform(json(post("/api/public/payments/" + paymentId + "/sandbox")).content(card("4000000000000002")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("CARD_DECLINED"));
        assertThat(paymentStatusOf(id)).isEqualTo("UNPAID");

        mvc.perform(json(post("/api/public/payments/" + paymentId + "/sandbox")).content(goodCard()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.cardBrand").value("visa"))
                .andExpect(jsonPath("$.cardLast4").value("1111"));
        assertThat(paymentStatusOf(id)).isEqualTo("PAID");
        // Only brand and last 4 are stored. / بس النوع وآخر 4 أرقام تنحفظ.
        assertThat(jdbc.queryForList("select * from payment").toString()).doesNotContain("4111111111111111");

        // Paid: no second payment. / مدفوع: ما فيه دفعة ثانية.
        mvc.perform(json(post("/api/public/requests/" + referenceOf(id) + "/pay")).content(payBody(PHONE, "TABBY")))
                .andExpect(status().isConflict());
        mvc.perform(auth(get("/api/requests/" + id), dispatcherToken()))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.payments.length()").value(1))
                .andExpect(jsonPath("$.payments[0].method").value("CARD"));
    }

    @Test
    void pressingPayTwiceReusesTheOpenAttempt() throws Exception {
        long id = acceptedAcRequest(dispatcherToken(), PHONE);
        String first = startPayment(referenceOf(id));
        String second = startPayment(referenceOf(id));
        assertThat(second).isEqualTo(first);
        assertThat(jdbc.queryForObject("select count(*) from payment", Long.class)).isEqualTo(1);
    }

    @Test
    void testCardsAreChecked() throws Exception {
        long id = acceptedAcRequest(dispatcherToken(), PHONE);
        String paymentId = startPayment(referenceOf(id));
        String url = "/api/public/payments/" + paymentId + "/sandbox";
        mvc.perform(json(post(url)).content(card("4111 1111 1111 1112")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.cardNumber").value("CardNumber"));
        mvc.perform(json(post(url)).content(goodCard().replace("\"expYear\":" + (time.today().getYear() + 2),
                        "\"expYear\":" + (time.today().getYear() - 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.expYear").value("CardExpired"));
        mvc.perform(json(post(url)).content(goodCard().replace("\"123\"", "\"12\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.cvc").value("Pattern"));
        mvc.perform(json(post(url)).content(card("4000000000009995")))
                .andExpect(jsonPath("$.failureReason").value("INSUFFICIENT_FUNDS"));
        mvc.perform(get("/api/public/payments/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    void theTechnicianRecordsCashWhenCompletingAndOnlinePaymentCloses() throws Exception {
        String office = dispatcherToken();
        long id = acceptedAcRequest(office, PHONE);
        // An open card attempt from before the visit. / محاولة بطاقة مفتوحة من قبل الزيارة.
        String paymentId = startPayment(referenceOf(id));
        schedule(id, office, userId("fahad"), tomorrow(), "09:00").andExpect(status().isOk());
        String fahad = technicianToken("fahad");
        mvc.perform(auth(post("/api/requests/" + id + "/start"), fahad).content("{\"version\":%d}".formatted(versionOf(id))))
                .andExpect(status().isOk());

        // Another technician cannot record money on this job. / فني ثاني ما يقدر يسجل فلوس على هالشغل.
        mvc.perform(auth(post("/api/requests/" + id + "/payments/cash"), technicianToken("omar"))
                        .content("{\"version\":%d}".formatted(versionOf(id))))
                .andExpect(status().isForbidden());

        mvc.perform(auth(post("/api/requests/" + id + "/complete"), fahad).content("""
                        {"note":"Done","cashCollected":true,"version":%d}""".formatted(versionOf(id))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.payments[?(@.method == 'CASH')].recordedBy").value("fahad"))
                .andExpect(jsonPath("$.payments[?(@.method == 'CARD')].failureReason").value("SUPERSEDED"));

        // The old card link no longer takes money. / رابط البطاقة القديم ما عاد ياخذ فلوس.
        mvc.perform(json(post("/api/public/payments/" + paymentId + "/sandbox")).content(goodCard()))
                .andExpect(status().isConflict());
        mvc.perform(auth(post("/api/requests/" + id + "/payments/cash"), office)
                        .content("{\"version\":%d}".formatted(versionOf(id))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_ALLOWED"));
    }

    @Test
    void cancellingAPaidRequestRefundsIt() throws Exception {
        String office = dispatcherToken();
        long id = acceptedAcRequest(office, PHONE);
        String paymentId = startPayment(referenceOf(id));
        mvc.perform(json(post("/api/public/payments/" + paymentId + "/sandbox")).content(goodCard()))
                .andExpect(jsonPath("$.status").value("PAID"));

        mvc.perform(auth(post("/api/requests/" + id + "/cancel"), office)
                        .content("{\"reason\":\"Customer moved\",\"version\":%d}".formatted(versionOf(id))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.payments[0].status").value("REFUNDED"))
                .andExpect(jsonPath("$.payments[0].refundedAt").isNotEmpty());
        mvc.perform(json(post("/api/public/requests/track")).content("""
                        {"reference":"%s","phone":"%s"}""".formatted(referenceOf(id), PHONE)))
                .andExpect(jsonPath("$.payment.status").value("REFUNDED"));
        // Money in minus refunds: nothing collected this month. / الداخل ناقص المسترجع: ما انْجمع شي هالشهر.
        mvc.perform(auth(get("/api/dashboard"), office)).andExpect(jsonPath("$.collectedThisMonth").value(0));
    }

    @Test
    void aSignedInCustomerPaysFromTheirAccount() throws Exception {
        String token = registerCustomer("Reem", PHONE);
        String ref = JsonPath.read(mvc.perform(auth(post("/api/account/requests"), token)
                        .content(bookingJson(acId, "RIYADH", PHONE, tomorrow(), "MORNING")))
                .andReturn().getResponse().getContentAsString(), "$.reference");
        long id = requestId(ref);
        quoteAndSend(id, dispatcherToken(), "200.00");
        mvc.perform(auth(post("/api/account/requests/" + ref + "/accept"), token)).andExpect(status().isOk());
        String paymentId = JsonPath.read(mvc.perform(auth(post("/api/account/requests/" + ref + "/pay"), token)
                        .content("{\"method\":\"CARD\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.paymentId");
        mvc.perform(json(post("/api/public/payments/" + paymentId + "/sandbox")).content(card("5888 4500 0000 0003")))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.cardBrand").value("mada"));
        mvc.perform(auth(get("/api/account/requests/" + ref), token))
                .andExpect(jsonPath("$.payment.status").value("PAID"))
                .andExpect(jsonPath("$.payment.method").value("CARD"))
                .andExpect(jsonPath("$.payment.cardLast4").value("0003"))
                .andExpect(jsonPath("$.payment.payable").value(false));
        mvc.perform(auth(get("/api/dashboard"), dispatcherToken())).andExpect(jsonPath("$.collectedThisMonth").value(230.00));
    }

    @Test
    void applePayTabbyAndTamaraWorkInTheSandbox() throws Exception {
        String office = dispatcherToken();
        long id = acceptedAcRequest(office, PHONE);
        String ref = referenceOf(id);
        // The track page offers every method, with the 4-instalment plan (345 = 86.25 x 4).
        // صفحة التتبع تعرض كل الطرق، مع خطة الـ 4 دفعات (345 = 86.25 × 4).
        mvc.perform(json(post("/api/public/requests/track")).content("""
                        {"reference":"%s","phone":"%s"}""".formatted(ref, PHONE)))
                .andExpect(jsonPath("$.payment.methods.length()").value(4))
                .andExpect(jsonPath("$.payment.instalments.length()").value(4))
                .andExpect(jsonPath("$.payment.instalments[0].amount").value(86.25))
                .andExpect(jsonPath("$.payment.instalments[3].dueDate").value(time.today().plusMonths(3).toString()));

        // Apple Pay cancelled, then Tabby cancelled, then Tamara approved. / Apple Pay انلغى، بعدين تابي، بعدين تمارا وافق.
        String apple = startPayment(ref, "APPLE_PAY");
        mvc.perform(json(post("/api/public/payments/" + apple + "/sandbox")).content("{\"approve\":false}"))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("CANCELLED_BY_CUSTOMER"));
        String tabby = startPayment(ref, "TABBY");
        assertThat(tabby).isNotEqualTo(apple);
        mvc.perform(get("/api/public/payments/" + tabby))
                .andExpect(jsonPath("$.method").value("TABBY"))
                .andExpect(jsonPath("$.instalments.length()").value(4));
        mvc.perform(json(post("/api/public/payments/" + tabby + "/sandbox")).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.approve").value("NotNull"));
        mvc.perform(json(post("/api/public/payments/" + tabby + "/sandbox")).content("{\"approve\":false}"))
                .andExpect(jsonPath("$.status").value("FAILED"));
        String tamara = startPayment(ref, "TAMARA");
        mvc.perform(json(post("/api/public/payments/" + tamara + "/sandbox")).content("{\"approve\":true}"))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.cardBrand").value("tamara"));
        mvc.perform(auth(get("/api/requests/" + id), office))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.payments.length()").value(3))
                .andExpect(jsonPath("$.payments[2].method").value("TAMARA"));
        // A refund goes back through the same company. / الاسترجاع يرجع عن طريق نفس الشركة.
        mvc.perform(auth(post("/api/requests/" + id + "/cancel"), office)
                        .content("{\"reason\":\"Changed plans\",\"version\":%d}".formatted(versionOf(id))))
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"));
    }

    @Test
    void instalmentsAreOfferedOnlyWithinTheirLimits() throws Exception {
        String office = dispatcherToken();
        // 50 + VAT = 57.50, under the 100 SAR minimum. / 50 + الضريبة = 57.50، تحت الحد الأدنى 100 ريال.
        String ref = book(acId, "RIYADH", PHONE);
        long id = requestId(ref);
        quoteAndSend(id, office, "50.00");
        accept(ref, PHONE);
        mvc.perform(json(post("/api/public/requests/track")).content("""
                        {"reference":"%s","phone":"%s"}""".formatted(ref, PHONE)))
                .andExpect(jsonPath("$.payment.methods.length()").value(2))
                .andExpect(jsonPath("$.payment.instalments.length()").value(0));
        mvc.perform(json(post("/api/public/requests/" + ref + "/pay")).content(payBody(PHONE, "TABBY")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSTALMENTS_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.min").value(100));
    }
}
