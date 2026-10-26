package com.masar.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.masar.support.IntegrationTest;

/**
 * Login, token handling and the access rules of each role.
 * تسجيل الدخول، والتعامل مع الرموز، وقواعد الصلاحيات لكل دور.
 */
class AuthAndSecurityTest extends IntegrationTest {

    @Test
    void loginReturnsTokenAndUserWithoutPasswordHash() throws Exception {
        mvc.perform(json(post("/api/auth/login"))
                        .content("{\"username\":\"NOURA\",\"password\":\"%s\"}".formatted(STAFF_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("DISPATCHER"))
                // The hash must never leak into any response. / ناتج التشفير لازم ما يطلع في أي رد.
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameError() throws Exception {
        // Same 401 + code for both, so an attacker cannot tell which usernames exist.
        // نفس 401 ونفس الرمز للحالتين، حتى ما يعرف المهاجم أي أسماء مستخدمين موجودة.
        for (String body : new String[]{"{\"username\":\"admin\",\"password\":\"wrong-pass\"}",
                "{\"username\":\"nobody\",\"password\":\"whatever1\"}"}) {
            mvc.perform(json(post("/api/auth/login")).content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
        }
    }

    @Test
    void staffEndpointsNeedATokenButPublicOnesDoNot() throws Exception {
        mvc.perform(get("/api/requests"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/public/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void disabledUserIsRefusedEvenWithAStillValidToken() throws Exception {
        String token = dispatcherToken();
        jdbc.update("update app_user set active = false where username = 'noura'");
        // The old token is still correctly signed, but the account is checked on every request.
        // الرمز القديم توقيعه لسا صحيح، لكن الحساب ينفحص مع كل طلب.
        mvc.perform(auth(get("/api/requests"), token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void technicianCannotUseOfficeEndpoints() throws Exception {
        String token = technicianToken("fahad");
        mvc.perform(auth(get("/api/requests"), token)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(auth(get("/api/dashboard"), token)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/schedule?date=" + tomorrow()), token)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/users"), token)).andExpect(status().isForbidden());
    }

    @Test
    void dispatcherCannotManageUsersOrTheCatalogue() throws Exception {
        String token = dispatcherToken();
        mvc.perform(auth(get("/api/users"), token)).andExpect(status().isForbidden());
        mvc.perform(auth(post("/api/services"), token).content("""
                        {"code":"X","nameAr":"x","nameEn":"x","basePrice":1,"durationMinutes":60,"active":true}
                        """))
                .andExpect(status().isForbidden());
        // But may read the catalogue and the technician list. / لكن يقدر يقرأ الخدمات وقائمة الفنيين.
        mvc.perform(auth(get("/api/services"), token)).andExpect(status().isOk());
        mvc.perform(auth(get("/api/technicians?city=RIYADH&serviceTypeId=" + acId), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("fahad"));
    }

    @Test
    void technicianOnlyOpensTheirOwnJobs() throws Exception {
        String office = dispatcherToken();
        long id = acceptedAcRequest(office, "0551112233");
        schedule(id, office, userId("fahad"), tomorrow(), "10:00").andExpect(status().isOk());

        mvc.perform(auth(get("/api/requests/" + id), technicianToken("fahad")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
        mvc.perform(auth(get("/api/requests/" + id), technicianToken("omar")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ASSIGNED"));
        mvc.perform(auth(post("/api/requests/" + id + "/start"), technicianToken("omar"))
                        .content("{\"version\":%d}".formatted(versionOf(id))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ASSIGNED"));
    }

    @Test
    void sortByUnknownFieldIsAValidationErrorNotAServerError() throws Exception {
        mvc.perform(auth(get("/api/requests?sort=password"), dispatcherToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
