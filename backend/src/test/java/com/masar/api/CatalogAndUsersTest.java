package com.masar.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.masar.support.IntegrationTest;

/**
 * Admin screens: the service catalogue and staff accounts (technician skills and city).
 * شاشات المدير: قائمة الخدمات وحسابات الموظفين (مهارات الفني ومدينته).
 */
class CatalogAndUsersTest extends IntegrationTest {

    private static final String NEW_SERVICE = """
            {"code":"%s","nameAr":"تنظيف خزانات","nameEn":"Tank cleaning","basePrice":200,"durationMinutes":%d,
             "sortOrder":3,"active":true}""";

    @Test
    void adminManagesServicesAndDurationsSnapToHalfHours() throws Exception {
        String admin = adminToken();
        mvc.perform(auth(post("/api/services"), admin).content(NEW_SERVICE.formatted("TANK", 100)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationMinutes").value(120));
        mvc.perform(auth(post("/api/services"), admin).content(NEW_SERVICE.formatted("tank", 60)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.code").value("Pattern"));
        mvc.perform(auth(post("/api/services"), admin).content(NEW_SERVICE.formatted("AC", 60)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CODE_EXISTS"));
        mvc.perform(auth(post("/api/services"), admin).content(NEW_SERVICE.formatted("LONG", 600)))
                .andExpect(jsonPath("$.fields.durationMinutes").value("Max"));
    }

    @Test
    void aBookedServiceCannotBeDeletedOnlySwitchedOff() throws Exception {
        String admin = adminToken();
        book(acId, "RIYADH", "0551234567");
        mvc.perform(auth(delete("/api/services/" + acId), admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IN_USE"))
                .andExpect(jsonPath("$.requests").value(1));
        long version = jdbc.queryForObject("select version from service_type where id = ?", Long.class, acId);
        mvc.perform(auth(put("/api/services/" + acId), admin).content("""
                        {"code":"AC","nameAr":"مكيفات","nameEn":"AC","basePrice":150,"durationMinutes":90,
                         "sortOrder":1,"active":false,"version":%d}""".formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/public/services")).andExpect(jsonPath("$[*].code").value(org.hamcrest.Matchers.contains("PLUMBING")));
        // A never-booked service can be deleted. / الخدمة اللي ما انحجزت تنحذف.
        mvc.perform(auth(delete("/api/services/" + plumbingId), admin)).andExpect(status().isNoContent());
    }

    @Test
    void technicianAccountsNeedACityAndAtLeastOneSkill() throws Exception {
        String admin = adminToken();
        String base = """
                {"username":"ali","fullName":"Ali","password":"Passw0rd!","role":"TECHNICIAN","phone":"%s"%s}""";
        mvc.perform(auth(post("/api/users"), admin).content(base.formatted("0551234567", ",\"skillIds\":[" + acId + "]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.city").value("NotNull"));
        mvc.perform(auth(post("/api/users"), admin).content(base.formatted("0551234567", ",\"city\":\"RIYADH\"")))
                .andExpect(jsonPath("$.fields.skillIds").value("NotEmpty"));
        mvc.perform(auth(post("/api/users"), admin)
                        .content(base.formatted("0551234567", ",\"city\":\"RIYADH\",\"skillIds\":[999]")))
                .andExpect(jsonPath("$.fields.skillIds").value("NotFound"));
        mvc.perform(auth(post("/api/users"), admin)
                        .content(base.formatted("12345", ",\"city\":\"RIYADH\",\"skillIds\":[" + acId + "]")))
                .andExpect(jsonPath("$.fields.phone").value("Phone"));
        mvc.perform(auth(post("/api/users"), admin)
                        .content(base.formatted("0551234567", ",\"city\":\"RIYADH\",\"skillIds\":[" + acId + "," + plumbingId + "]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").value("966551234567"))
                .andExpect(jsonPath("$.skills.length()").value(2));
        mvc.perform(auth(get("/api/technicians?city=RIYADH&serviceTypeId=" + plumbingId), dispatcherToken()))
                .andExpect(jsonPath("$[*].username").value(org.hamcrest.Matchers.containsInAnyOrder("ali", "omar")));
    }

    @Test
    void adminCannotDemoteOrDisableThemselves() throws Exception {
        mvc.perform(auth(put("/api/users/" + userId("admin")), adminToken())
                        .content("{\"fullName\":\"Admin\",\"role\":\"DISPATCHER\",\"active\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_MODIFY_SELF"));
    }
}
