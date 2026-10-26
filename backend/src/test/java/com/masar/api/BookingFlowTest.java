package com.masar.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.masar.support.IntegrationTest;

/**
 * The customer side: booking, tracking, and accepting or rejecting the quote.
 * جهة العميل: الحجز، والتتبع، وقبول أو رفض عرض السعر.
 */
class BookingFlowTest extends IntegrationTest {

    @Test
    void bookingNormalisesThePhoneAndTrackingAcceptsAnyCommonFormat() throws Exception {
        String ref = book(acId, "RIYADH", "+966 55 123 4567");
        assertThat(ref).matches("MS-[23456789A-HJKMNP-Z]{8}");
        assertThat(jdbc.queryForObject("select phone from service_request where reference = ?", String.class, ref))
                .isEqualTo("966551234567");

        // Lower-case reference with spaces and a local phone format still finds it.
        // الرقم المرجعي بحروف صغيرة ومسافات والجوال بالصيغة المحلية يلقاه برضو.
        mvc.perform(json(post("/api/public/requests/track"))
                        .content("{\"reference\":\" %s \",\"phone\":\"0551234567\"}".formatted(ref.toLowerCase())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.quote").doesNotExist())
                .andExpect(jsonPath("$.history[0].status").value("NEW"))
                // Staff usernames are never shown to customers. / أسماء دخول الموظفين ما تظهر للعميل أبدًا.
                .andExpect(jsonPath("$.history[0].actor").doesNotExist());
    }

    @Test
    void wrongPhoneLooksExactlyLikeAnUnknownReference() throws Exception {
        String ref = book(acId, "RIYADH", "0551234567");
        for (String body : new String[]{
                "{\"reference\":\"%s\",\"phone\":\"0559999999\"}".formatted(ref),
                "{\"reference\":\"MS-AAAAAAAA\",\"phone\":\"0551234567\"}",
                "{\"reference\":\"%s\",\"phone\":\"not a phone\"}".formatted(ref)}) {
            mvc.perform(json(post("/api/public/requests/track")).content(body))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }

    @Test
    void bookingValidation() throws Exception {
        // Not a Saudi mobile. / مو جوال سعودي.
        mvc.perform(json(post("/api/public/requests"))
                        .content(bookingJson(acId, "RIYADH", "0112345678", tomorrow(), "MORNING")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.phone").value("Phone"));
        // In the past, and too far ahead. / في الماضي، وبعيد مرة.
        mvc.perform(json(post("/api/public/requests"))
                        .content(bookingJson(acId, "RIYADH", "0551234567", time.today().minusDays(1), "MORNING")))
                .andExpect(jsonPath("$.fields.preferredDate").value("FutureOrPresent"));
        mvc.perform(json(post("/api/public/requests"))
                        .content(bookingJson(acId, "RIYADH", "0551234567", time.today().plusDays(61), "MORNING")))
                .andExpect(jsonPath("$.fields.preferredDate").value("Max"));
        // Missing required fields come back per field. / الحقول الناقصة ترجع لكل حقل.
        mvc.perform(json(post("/api/public/requests")).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.customerName").value("NotBlank"))
                .andExpect(jsonPath("$.fields.city").value("NotNull"));
        // A switched-off service cannot be booked. / الخدمة الموقوفة ما تنحجز.
        jdbc.update("update service_type set active = false where id = ?", acId);
        mvc.perform(json(post("/api/public/requests"))
                        .content(bookingJson(acId, "RIYADH", "0551234567", tomorrow(), "MORNING")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SERVICE_INACTIVE"));
        mvc.perform(get("/api/public/services")).andExpect(jsonPath("$.length()").value(1));
        assertThat(jdbc.queryForObject("select count(*) from service_request", Long.class)).isZero();
    }

    @Test
    void honeypotLooksLikeSuccessButSavesNothing() throws Exception {
        String body = bookingJson(acId, "RIYADH", "0551234567", tomorrow(), "MORNING")
                .replace("\"customerName\"", "\"website\":\"http://spam.example\",\"customerName\"");
        mvc.perform(json(post("/api/public/requests")).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").isNotEmpty());
        assertThat(jdbc.queryForObject("select count(*) from service_request", Long.class)).isZero();
    }

    @Test
    void draftQuoteIsHiddenUntilSentThenTheCustomerAccepts() throws Exception {
        String office = dispatcherToken();
        String ref = book(acId, "RIYADH", "0551234567");
        long id = requestId(ref);
        mvc.perform(auth(put("/api/requests/" + id + "/quote"), office).content("""
                        {"lines":[{"description":"Clean","quantity":2,"unitPrice":150}],"validUntil":"%s","version":%d}
                        """.formatted(time.today().plusDays(7), versionOf(id))))
                .andExpect(status().isOk());
        mvc.perform(json(post("/api/public/requests/track"))
                        .content("{\"reference\":\"%s\",\"phone\":\"0551234567\"}".formatted(ref)))
                .andExpect(jsonPath("$.quote").doesNotExist());
        // Accepting before the quote is sent is refused. / القبول قبل إرسال العرض مرفوض.
        mvc.perform(json(post("/api/public/requests/" + ref + "/accept")).content("{\"phone\":\"0551234567\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"))
                .andExpect(jsonPath("$.status").value("NEW"));

        mvc.perform(auth(post("/api/requests/" + id + "/quote/send"), office)
                        .content("{\"version\":%d}".formatted(versionOf(id))))
                .andExpect(status().isOk());
        mvc.perform(json(post("/api/public/requests/" + ref + "/accept")).content("{\"phone\":\"0551234567\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.quote.total").value(345.00));

        // History: NEW by customer, QUOTED by noura, ACCEPTED by customer.
        // السجل: NEW من العميل، QUOTED من noura، ACCEPTED من العميل.
        List<String> actors = jdbc.queryForList(
                "select actor || ':' || to_status from request_event where request_id = ? order by id", String.class, id);
        assertThat(actors).containsExactly("customer:NEW", "noura:QUOTED", "customer:ACCEPTED");
    }

    @Test
    void expiredQuoteCannotBeAccepted() throws Exception {
        String ref = book(acId, "RIYADH", "0551234567");
        long id = requestId(ref);
        quoteAndSend(id, dispatcherToken(), "100");
        jdbc.update("update quote set valid_until = ? where request_id = ?", time.today().minusDays(1), id);
        mvc.perform(json(post("/api/public/requests/" + ref + "/accept")).content("{\"phone\":\"0551234567\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUOTE_EXPIRED"));
        mvc.perform(json(post("/api/public/requests/track"))
                        .content("{\"reference\":\"%s\",\"phone\":\"0551234567\"}".formatted(ref)))
                .andExpect(jsonPath("$.quote.expired").value(true));
    }

    @Test
    void rejectingNeedsAReasonAndTheOfficeCanRevise() throws Exception {
        String office = dispatcherToken();
        String ref = book(acId, "RIYADH", "0551234567");
        long id = requestId(ref);
        quoteAndSend(id, office, "500");
        mvc.perform(json(post("/api/public/requests/" + ref + "/reject")).content("{\"phone\":\"0551234567\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.reason").value("NotBlank"));
        mvc.perform(json(post("/api/public/requests/" + ref + "/reject"))
                        .content("{\"phone\":\"0551234567\",\"reason\":\"Too expensive\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectReason").value("Too expensive"));

        // Revise: back to NEW, the reason is cleared, the old quote is hidden from the customer again.
        // تعديل: يرجع NEW، والسبب ينمسح، والعرض القديم يختفي عن العميل مرة ثانية.
        mvc.perform(auth(post("/api/requests/" + id + "/revise"), office)
                        .content("{\"version\":%d}".formatted(versionOf(id))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.rejectReason").doesNotExist());
        quoteAndSend(id, office, "400");
        accept(ref, "0551234567");
        assertThat(statusOf(id)).isEqualTo("ACCEPTED");
    }

    @Test
    void officeCanBookOnBehalfOfACaller() throws Exception {
        String body = mvc.perform(auth(post("/api/requests"), dispatcherToken())
                        .content(bookingJson(plumbingId, "RIYADH", "0557654321", tomorrow(), "EVENING")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = requestId(com.jayway.jsonpath.JsonPath.read(body, "$.reference"));
        assertThat(jdbc.queryForObject("select actor from request_event where request_id = ?", String.class, id))
                .isEqualTo("noura");
    }
}
