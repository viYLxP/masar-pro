package com.masar.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.masar.support.IntegrationTest;

/**
 * Dashboard numbers against data whose answer we know.
 * أرقام لوحة التحكم على بيانات نعرف جوابها.
 */
class DashboardTest extends IntegrationTest {

    @Test
    void emptyDatabaseGivesZerosAndNoAcceptanceRate() throws Exception {
        mvc.perform(auth(get("/api/dashboard"), dispatcherToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCounts.NEW").value(0))
                .andExpect(jsonPath("$.statusCounts.CANCELLED").value(0))
                .andExpect(jsonPath("$.revenueThisMonth").value(0))
                // No answered quotes: null, not a misleading 0%. / ما فيه عروض انرد عليها: null، مو 0% مضللة.
                .andExpect(jsonPath("$.acceptanceRate").doesNotExist());
    }

    @Test
    void countsRevenueAcceptanceAndLists() throws Exception {
        String office = dispatcherToken();
        // 1 completed job of 300 + 15% = 345. / شغل واحد مكتمل بـ 300 + 15% = 345.
        long done = acceptedAcRequest(office, "0551111111");
        schedule(done, office, userId("fahad"), tomorrow(), "09:00").andExpect(status().isOk());
        mvc.perform(auth(post("/api/requests/" + done + "/start"), office)
                .content("{\"version\":%d}".formatted(versionOf(done)))).andExpect(status().isOk());
        mvc.perform(auth(post("/api/requests/" + done + "/complete"), office)
                .content("{\"version\":%d}".formatted(versionOf(done)))).andExpect(status().isOk());
        // 2 more accepted, 1 rejected → 3 of 4 answered quotes accepted = 75%.
        // 2 مقبولة زيادة، و1 مرفوض ← 3 من 4 عروض مقبولة = 75%.
        acceptedAcRequest(office, "0552222222");
        acceptedAcRequest(office, "0553333333");
        String rejected = book(acId, "RIYADH", "0554444444");
        quoteAndSend(requestId(rejected), office, "900");
        mvc.perform(json(post("/api/public/requests/" + rejected + "/reject"))
                .content("{\"phone\":\"0554444444\",\"reason\":\"Price\"}")).andExpect(status().isOk());
        // A quote that runs out tomorrow, and one fresh request waiting for a price.
        // عرض ينتهي بكرة، وطلب جديد ينتظر سعر.
        String expiring = book(plumbingId, "RIYADH", "0555555555");
        quoteAndSend(requestId(expiring), office, "100");
        jdbc.update("update quote set valid_until = ? where request_id = ?", time.today().plusDays(1),
                requestId(expiring));
        String waiting = book(plumbingId, "RIYADH", "0556666666");
        // Completed last month must not count in this month's revenue. / الشغل المكتمل الشهر الماضي ما ينحسب في إيراد الشهر.
        jdbc.update("update service_request set completed_at = ? where id = ?",
                Timestamp.from(time.startOf(time.today().withDayOfMonth(1)).minus(Duration.ofHours(1))), done);

        mvc.perform(auth(get("/api/dashboard"), office))
                .andExpect(jsonPath("$.statusCounts.COMPLETED").value(1))
                .andExpect(jsonPath("$.statusCounts.ACCEPTED").value(2))
                .andExpect(jsonPath("$.statusCounts.REJECTED").value(1))
                .andExpect(jsonPath("$.statusCounts.QUOTED").value(1))
                .andExpect(jsonPath("$.statusCounts.NEW").value(1))
                .andExpect(jsonPath("$.revenueThisMonth").value(0))
                .andExpect(jsonPath("$.completedThisMonth").value(0))
                .andExpect(jsonPath("$.acceptanceRate").value(75))
                .andExpect(jsonPath("$.waitingForQuote[0].reference").value(waiting))
                .andExpect(jsonPath("$.expiringQuotes[0].reference").value(expiring))
                .andExpect(jsonPath("$.topServices[0].service.code").value("AC"))
                .andExpect(jsonPath("$.topServices[0].count").value(4));

        jdbc.update("update service_request set completed_at = now() where id = ?", done);
        mvc.perform(auth(get("/api/dashboard"), office))
                .andExpect(jsonPath("$.revenueThisMonth").value(345.00))
                .andExpect(jsonPath("$.completedThisMonth").value(1));
    }
}
