package com.masar.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;

import com.masar.support.IntegrationTest;

/**
 * Customer accounts: sign-up, login by mobile, "My requests", and the wall between customers and staff.
 * حسابات العملاء: التسجيل، والدخول بالجوال، و"طلباتي"، والفصل بين العملاء والموظفين.
 */
class AccountTest extends IntegrationTest {

    @Test
    void signUpLogsInAndTheMobileWorksInAnyCommonFormat() throws Exception {
        String token = registerCustomer("Reem", "0551112233");
        mvc.perform(auth(get("/api/auth/me"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.username").value("966551112233"))
                .andExpect(jsonPath("$.phone").value("966551112233"))
                .andExpect(jsonPath("$.email").value("c@example.com"));
        login("+966 55 111 2233", CUSTOMER_PASSWORD);
        login("551112233", CUSTOMER_PASSWORD);

        // One account per number, however it is typed. / حساب واحد لكل رقم، مهما انكتب.
        mvc.perform(json(post("/api/auth/register")).content("""
                        {"fullName":"Other","phone":"00966551112233","password":"Another@123"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PHONE_REGISTERED"));
        mvc.perform(json(post("/api/auth/register")).content("""
                        {"fullName":"X","phone":"0112345678","email":"not-an-email","password":"short"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.password").value("Size"))
                .andExpect(jsonPath("$.fields.email").value("Email"));
        mvc.perform(json(post("/api/auth/register")).content("""
                        {"fullName":"X","phone":"0112345678","password":"LongEnough1"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.phone").value("Phone"));
        // The password is stored as a BCrypt hash. / كلمة المرور تنحفظ BCrypt.
        assertThat(jdbc.queryForObject("select password_hash from app_user where username = '966551112233'",
                String.class)).startsWith("$2");
    }

    @Test
    void customersAreKeptOutOfTheStaffAppAndStaffOutOfAccounts() throws Exception {
        String customer = registerCustomer("Reem", "0551112233");
        for (String path : new String[]{"/api/requests", "/api/dashboard", "/api/users", "/api/schedule",
                "/api/jobs/mine", "/api/technicians", "/api/services"}) {
            mvc.perform(auth(get(path), customer)).andExpect(status().isForbidden());
        }
        String staff = dispatcherToken();
        mvc.perform(auth(get("/api/account/requests"), staff)).andExpect(status().isForbidden());
        mvc.perform(get("/api/account/requests")).andExpect(status().isUnauthorized());
    }

    @Test
    void aBookingFromTheAccountUsesTheAccountMobileAndShowsInMyRequests() throws Exception {
        String token = registerCustomer("Reem", "0551112233");
        // The form sends another number; the account's own number is used. / النموذج يرسل رقم ثاني؛ وينستخدم رقم الحساب.
        String body = mvc.perform(auth(post("/api/account/requests"), token)
                        .content(bookingJson(acId, "RIYADH", "0559999999", tomorrow(), "MORNING")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String ref = JsonPath.read(body, "$.reference");
        assertThat(jdbc.queryForObject("select phone from service_request where reference = ?", String.class, ref))
                .isEqualTo("966551112233");

        mvc.perform(auth(get("/api/account/requests"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reference").value(ref))
                .andExpect(jsonPath("$[0].status").value("NEW"))
                .andExpect(jsonPath("$[0].payable").value(false))
                .andExpect(jsonPath("$[0].paymentStatus").value("UNPAID"));
        // The office sees that the customer has an account. / المكتب يشوف إن العميل عنده حساب.
        mvc.perform(auth(get("/api/requests/" + requestId(ref)), dispatcherToken()))
                .andExpect(jsonPath("$.registeredCustomer").value(true));
    }

    @Test
    void aCustomerCannotOpenOrActOnAnotherCustomersRequest() throws Exception {
        String reem = registerCustomer("Reem", "0551112233");
        String sara = registerCustomer("Sara", "0554445566");
        String ref = JsonPath.read(mvc.perform(auth(post("/api/account/requests"), reem)
                        .content(bookingJson(acId, "RIYADH", "0551112233", tomorrow(), "MORNING")))
                .andReturn().getResponse().getContentAsString(), "$.reference");
        long id = requestId(ref);
        quoteAndSend(id, dispatcherToken(), "300.00");

        // Same answer as a reference that does not exist. / نفس رد الرقم المرجعي اللي ما هو موجود.
        mvc.perform(auth(get("/api/account/requests/" + ref), sara)).andExpect(status().isNotFound());
        mvc.perform(auth(post("/api/account/requests/" + ref + "/accept"), sara)).andExpect(status().isNotFound());
        mvc.perform(auth(post("/api/account/requests/" + ref + "/pay"), sara).content("{\"method\":\"CARD\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(auth(post("/api/account/requests/claim"), sara).content("{\"reference\":\"" + ref + "\"}"))
                .andExpect(status().isNotFound());
        assertThat(statusOf(id)).isEqualTo("QUOTED");

        // The owner sees the sent quote and accepts it. / صاحبة الطلب تشوف العرض المرسل وتقبله.
        mvc.perform(auth(get("/api/account/requests/" + ref), reem))
                .andExpect(jsonPath("$.quote.total").value(345.00));
        mvc.perform(auth(post("/api/account/requests/" + ref + "/accept"), reem))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.payment.payable").value(true))
                .andExpect(jsonPath("$.payment.amountDue").value(345.00));
    }

    @Test
    void aGuestBookingWithTheSameMobileCanBeAddedToTheAccount() throws Exception {
        String ref = book(acId, "RIYADH", "0551112233");
        String token = registerCustomer("Reem", "055 111 2233");
        mvc.perform(auth(post("/api/account/requests/claim"), token).content("{\"reference\":\"" + ref.toLowerCase() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value(ref));
        mvc.perform(auth(get("/api/account/requests"), token)).andExpect(jsonPath("$.length()").value(1));

        // A booking made with another number cannot be claimed. / الحجز برقم ثاني ما ينضاف.
        String other = book(acId, "RIYADH", "0557778888");
        mvc.perform(auth(post("/api/account/requests/claim"), token).content("{\"reference\":\"" + other + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void profileAndPasswordChange() throws Exception {
        String token = registerCustomer("Reem", "0551112233");
        mvc.perform(auth(put("/api/account/profile"), token).content("{\"fullName\":\" Reem Q \",\"email\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Reem Q"))
                .andExpect(jsonPath("$.email").doesNotExist());
        mvc.perform(auth(put("/api/account/password"), token)
                        .content("{\"currentPassword\":\"wrong-one\",\"newPassword\":\"NewSecret@1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));
        mvc.perform(auth(put("/api/account/password"), token)
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"NewSecret@1\"}".formatted(CUSTOMER_PASSWORD)))
                .andExpect(status().isNoContent());
        login("0551112233", "NewSecret@1");
    }

    @Test
    void theAdminCanSwitchACustomerOffButNotTurnAccountsIntoOtherKinds() throws Exception {
        String customer = registerCustomer("Reem", "0551112233");
        String admin = adminToken();
        long id = userId("966551112233");
        // Customers sign up themselves. / العملاء يسجلون بنفسهم.
        mvc.perform(auth(post("/api/users"), admin).content("""
                        {"username":"cust1","fullName":"C","password":"Password1","role":"CUSTOMER"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.role").value("Staff"));
        mvc.perform(auth(put("/api/users/" + id), admin).content("""
                        {"fullName":"Reem","role":"DISPATCHER","active":true}"""))
                .andExpect(status().isBadRequest());
        mvc.perform(auth(put("/api/users/" + id), admin).content("""
                        {"fullName":"Ignored","role":"CUSTOMER","active":false}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.fullName").value("Reem"));
        // Switched off: the old token stops working at once. / انوقف: الرمز القديم يوقف على طول.
        mvc.perform(auth(get("/api/account/requests"), customer)).andExpect(status().isUnauthorized());
        mvc.perform(auth(get("/api/users"), admin)).andExpect(jsonPath("$[?(@.role == 'CUSTOMER')].length()").exists());
    }
}
