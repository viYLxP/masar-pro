package com.masar.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.masar.support.IntegrationTest;

/**
 * The public endpoints refuse a visitor who sends too many requests.
 * الروابط العامة ترفض الزائر اللي يرسل طلبات كثيرة.
 */
@TestPropertySource(properties = {"masar.rate-limit.bookings=2", "masar.rate-limit.lookups=3"})
class RateLimitTest extends IntegrationTest {

    @Test
    void bookingsAndLookupsHaveSeparateLimitsPerIp() throws Exception {
        String body = bookingJson(acId, "RIYADH", "0551234567", tomorrow(), "MORNING");
        mvc.perform(json(post("/api/public/requests")).content(body)).andExpect(status().isCreated());
        mvc.perform(json(post("/api/public/requests")).content(body)).andExpect(status().isCreated());
        mvc.perform(json(post("/api/public/requests")).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        // Another IP is not affected. / عنوان ثاني ما يتأثر.
        mvc.perform(json(post("/api/public/requests")).content(body).with(r -> {
            r.setRemoteAddr("10.9.9.9");
            return r;
        })).andExpect(status().isCreated());

        // Guessing phone numbers on the tracking endpoint is limited too. / تخمين الأرقام في التتبع محدود كمان.
        String track = "{\"reference\":\"MS-AAAAAAAA\",\"phone\":\"0550000000\"}";
        for (int i = 0; i < 3; i++) {
            mvc.perform(json(post("/api/public/requests/track")).content(track)).andExpect(status().isNotFound());
        }
        mvc.perform(json(post("/api/public/requests/track")).content(track))
                .andExpect(status().isTooManyRequests());
    }
}
