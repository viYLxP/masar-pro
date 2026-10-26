package com.masar.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.masar.support.IntegrationTest;

/**
 * Writing and sending quotes: totals with 15% VAT, the edit window and concurrent edits.
 * كتابة وإرسال عروض الأسعار: الإجماليات مع ضريبة 15%، ومتى يسمح التعديل، والتعديل المتزامن.
 */
class QuoteTest extends IntegrationTest {

    private String quoteJson(String lines, long id) {
        return """
                {"lines":[%s],"validUntil":"%s","notes":"Includes materials","version":%d}
                """.formatted(lines, time.today().plusDays(7), versionOf(id));
    }

    @Test
    void totalsUseVatOnTheSubtotalRoundedHalfUp() throws Exception {
        long id = requestId(book(acId, "RIYADH", "0551234567"));
        // 3 × 33.33 + 1 × 0.01 = 100.00 → VAT 15.00 → 115.00
        mvc.perform(auth(put("/api/requests/" + id + "/quote"), dispatcherToken()).content(quoteJson("""
                        {"description":"Unit","quantity":3,"unitPrice":33.33},
                        {"description":"Extra","quantity":1,"unitPrice":0.01}""", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quote.lines[0].lineTotal").value(99.99))
                .andExpect(jsonPath("$.quote.subtotal").value(100.00))
                .andExpect(jsonPath("$.quote.vat").value(15.00))
                .andExpect(jsonPath("$.quote.total").value(115.00));
        // 0.30 × 15% = 0.045 → half-up gives 0.05; half-even would give 0.04.
        // 0.30 × 15% = 0.045 ← التقريب نص فوق يعطي 0.05؛ وتقريب البنوك كان بيعطي 0.04.
        mvc.perform(auth(put("/api/requests/" + id + "/quote"), dispatcherToken()).content(quoteJson("""
                        {"description":"Tiny","quantity":1,"unitPrice":0.30}""", id)))
                .andExpect(jsonPath("$.quote.vat").value(0.05))
                .andExpect(jsonPath("$.quote.total").value(0.35))
                // Rewriting replaced the lines instead of adding to them. / إعادة الكتابة استبدلت البنود بدل ما تضيف عليها.
                .andExpect(jsonPath("$.quote.lines.length()").value(1));
    }

    @Test
    void quoteValidation() throws Exception {
        String token = dispatcherToken();
        long id = requestId(book(acId, "RIYADH", "0551234567"));
        mvc.perform(auth(put("/api/requests/" + id + "/quote"), token).content("""
                        {"lines":[],"validUntil":"%s","version":%d}""".formatted(time.today(), versionOf(id))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.lines").value("NotEmpty"));
        mvc.perform(auth(put("/api/requests/" + id + "/quote"), token).content("""
                        {"lines":[{"description":"x","quantity":0,"unitPrice":-1}],"validUntil":"%s","version":%d}
                        """.formatted(time.today(), versionOf(id))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields['lines[0].quantity']").value("Min"))
                .andExpect(jsonPath("$.fields['lines[0].unitPrice']").value("DecimalMin"));
        mvc.perform(auth(put("/api/requests/" + id + "/quote"), token).content("""
                        {"lines":[{"description":"x","quantity":1,"unitPrice":1}],"validUntil":"%s","version":%d}
                        """.formatted(time.today().minusDays(1), versionOf(id))))
                .andExpect(jsonPath("$.fields.validUntil").value("FutureOrPresent"));
    }

    @Test
    void sendingNeedsAQuoteAndASentQuoteIsLocked() throws Exception {
        String token = dispatcherToken();
        long id = requestId(book(acId, "RIYADH", "0551234567"));
        mvc.perform(auth(post("/api/requests/" + id + "/quote/send"), token)
                        .content("{\"version\":%d}".formatted(versionOf(id))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUOTE_REQUIRED"));
        quoteAndSend(id, token, "200");
        assertThat(statusOf(id)).isEqualTo("QUOTED");
        // Changing the price after the customer saw it is refused until the quote is withdrawn (revise).
        // تغيير السعر بعد ما شافه العميل مرفوض لين يتسحب العرض (تعديل).
        mvc.perform(auth(put("/api/requests/" + id + "/quote"), token)
                        .content(quoteJson("{\"description\":\"x\",\"quantity\":1,\"unitPrice\":1}", id)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"))
                .andExpect(jsonPath("$.status").value("QUOTED"));
    }

    @Test
    void savingAQuoteMakesOtherOpenCopiesStale() throws Exception {
        String token = dispatcherToken();
        long id = requestId(book(acId, "RIYADH", "0551234567"));
        long loaded = versionOf(id);
        mvc.perform(auth(put("/api/requests/" + id + "/quote"), token)
                        .content(quoteJson("{\"description\":\"x\",\"quantity\":1,\"unitPrice\":1}", id)))
                .andExpect(status().isOk())
                // The response already carries the new version, so the same user can keep working.
                // الرد يحمل رقم النسخة الجديد، فنفس المستخدم يكمل شغله.
                .andExpect(jsonPath("$.version").value(loaded + 1));
        // A second dispatcher who loaded the request earlier tries to send with the old version.
        // موزّع ثاني حمّل الطلب قبل، يحاول يرسل برقم النسخة القديم.
        mvc.perform(auth(post("/api/requests/" + id + "/quote/send"), token)
                        .content("{\"version\":%d}".formatted(loaded)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));
    }

    @Test
    void cancelNeedsAReasonAndIsFinal() throws Exception {
        String token = dispatcherToken();
        long id = requestId(book(acId, "RIYADH", "0551234567"));
        mvc.perform(auth(post("/api/requests/" + id + "/cancel"), token)
                        .content("{\"reason\":\" \",\"version\":%d}".formatted(versionOf(id))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.reason").value("NotBlank"));
        mvc.perform(auth(post("/api/requests/" + id + "/cancel"), token)
                        .content("{\"reason\":\"Duplicate\",\"version\":%d}".formatted(versionOf(id))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(auth(post("/api/requests/" + id + "/revise"), token)
                        .content("{\"version\":%d}".formatted(versionOf(id))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"));
    }
}
