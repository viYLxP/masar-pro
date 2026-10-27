package com.masar.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.masar.catalog.ServiceType;
import com.masar.catalog.ServiceTypeRepository;
import com.masar.common.BusinessTime;
import com.masar.common.City;
import com.masar.user.AppUser;
import com.masar.user.AppUserRepository;
import com.masar.user.Role;

/**
 * Base class for integration tests: real Spring context + a REAL PostgreSQL in Docker (Testcontainers).
 * الكلاس الأساسي لاختبارات التكامل: سياق Spring حقيقي + PostgreSQL حقيقي داخل Docker (Testcontainers).
 *
 * <p>We test against PostgreSQL instead of an in-memory database so the Flyway migrations, CHECK constraints
 * and cascades are exactly the ones used in production.</p>
 * <p>نختبر على PostgreSQL بدل قاعدة بيانات في الذاكرة حتى تكون ترحيلات Flyway وقيود CHECK والحذف المتسلسل
 * هي نفسها اللي في الإنتاج.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    // One container shared by all test classes (started once), which keeps the suite fast.
    // حاوية وحدة مشتركة بين كل كلاسات الاختبار (تشتغل مرة وحدة)، وهذا يخلي الاختبارات سريعة.
    // @ServiceConnection points spring.datasource at this container automatically.
    // @ServiceConnection يوجّه إعدادات قاعدة البيانات لهذي الحاوية تلقائيًا.
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    protected static final String ADMIN_PASSWORD = "Admin@12345";
    protected static final String STAFF_PASSWORD = "Staff@12345";
    protected static final String CUSTOMER_PASSWORD = "Customer@123";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private ServiceTypeRepository services;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    protected BusinessTime time;

    // Service ids created for every test. / أرقام الخدمات اللي تنشأ لكل اختبار.
    protected long acId;
    protected long plumbingId;

    /**
     * Wipes all data before each test so tests never depend on each other's leftovers.
     * Staff: admin, a dispatcher (noura), and technicians: fahad (Riyadh, AC), omar (Riyadh, plumbing),
     * yousef (Jeddah, AC), so skill and city matching can be tested.
     * يمسح كل البيانات قبل كل اختبار حتى ما يعتمد أي اختبار على بقايا اختبار ثاني.
     * الموظفين: مدير، وموزّعة (noura)، وفنيين: fahad (الرياض، مكيفات) و omar (الرياض، سباكة)
     * و yousef (جدة، مكيفات)، حتى نختبر مطابقة المهارة والمدينة.
     */
    @BeforeEach
    void resetDatabase() {
        jdbc.execute("""
                truncate table request_event, quote_line, quote, service_request, technician_skill, service_type,
                    app_user restart identity cascade
                """);
        ServiceType ac = service("AC", "150.00", 90);
        ServiceType plumbing = service("PLUMBING", "120.00", 60);
        acId = ac.getId();
        plumbingId = plumbing.getId();
        users.save(new AppUser("admin", "Admin", passwordEncoder.encode(ADMIN_PASSWORD), Role.ADMIN));
        users.save(new AppUser("noura", "Noura", passwordEncoder.encode(STAFF_PASSWORD), Role.DISPATCHER));
        technician("fahad", City.RIYADH, ac);
        technician("omar", City.RIYADH, plumbing);
        technician("yousef", City.JEDDAH, ac);
    }

    private ServiceType service(String code, String price, int minutes) {
        ServiceType s = new ServiceType(code);
        s.setNameAr(code + " ar");
        s.setNameEn(code + " en");
        s.setBasePrice(new BigDecimal(price));
        s.setDurationMinutes(minutes);
        return services.save(s);
    }

    private void technician(String username, City city, ServiceType skill) {
        AppUser u = new AppUser(username, username.substring(0, 1).toUpperCase() + username.substring(1),
                passwordEncoder.encode(STAFF_PASSWORD), Role.TECHNICIAN);
        u.setCity(city);
        u.setPhone("96655000000" + (users.count() % 10));
        u.getSkills().add(skill);
        users.save(u);
    }

    /**
     * Logs in through the real endpoint and returns a JWT.
     * يسجّل دخول عبر الرابط الحقيقي ويرجّع رمز JWT.
     */
    protected String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    protected String adminToken() throws Exception {
        return login("admin", ADMIN_PASSWORD);
    }

    protected String dispatcherToken() throws Exception {
        return login("noura", STAFF_PASSWORD);
    }

    protected String technicianToken(String username) throws Exception {
        return login(username, STAFF_PASSWORD);
    }

    /**
     * Signs up a customer through the real endpoint and returns their token.
     * يسجّل عميل عبر الرابط الحقيقي ويرجّع رمز دخوله.
     */
    protected String registerCustomer(String name, String phone) throws Exception {
        String body = mvc.perform(json(post("/api/auth/register")).content("""
                        {"fullName":"%s","phone":"%s","email":"c@example.com","password":"%s"}
                        """.formatted(name, phone, CUSTOMER_PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    protected String paymentStatusOf(long requestId) {
        return jdbc.queryForObject("select payment_status from service_request where id = ?", String.class, requestId);
    }

    protected long userId(String username) {
        return jdbc.queryForObject("select id from app_user where username = ?", Long.class, username);
    }

    /**
     * Adds the bearer token and JSON content type to a request.
     * يضيف رمز الدخول ونوع المحتوى JSON للطلب.
     */
    protected static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    protected static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON);
    }

    /** Current optimistic-lock version of a request. / رقم النسخة الحالي للطلب. */
    protected long versionOf(long requestId) {
        return jdbc.queryForObject("select version from service_request where id = ?", Long.class, requestId);
    }

    protected long requestId(String reference) {
        return jdbc.queryForObject("select id from service_request where reference = ?", Long.class, reference);
    }

    protected String statusOf(long requestId) {
        return jdbc.queryForObject("select status from service_request where id = ?", String.class, requestId);
    }

    // ----- Fixture helpers / دوال تجهيز البيانات -----

    protected LocalDate tomorrow() {
        return time.today().plusDays(1);
    }

    protected static String bookingJson(long serviceId, String city, String phone, LocalDate date, String slot) {
        return """
                {"customerName":"Test Customer","phone":"%s","city":"%s","district":"Al Malqa",
                 "propertyType":"VILLA","serviceTypeId":%d,"preferredDate":"%s","preferredSlot":"%s"}
                """.formatted(phone, city, serviceId, date, slot);
    }

    /** Books through the public endpoint and returns the reference. / يحجز عبر الرابط العام ويرجّع الرقم المرجعي. */
    protected String book(long serviceId, String city, String phone) throws Exception {
        String body = mvc.perform(json(post("/api/public/requests"))
                        .content(bookingJson(serviceId, city, phone, tomorrow(), "MORNING")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.reference");
    }

    /** Writes a one-line quote (valid 7 days) and sends it. / يكتب عرض ببند واحد (صالح 7 أيام) ويرسله. */
    protected void quoteAndSend(long id, String token, String unitPrice) throws Exception {
        mvc.perform(auth(put("/api/requests/" + id + "/quote"), token).content("""
                        {"lines":[{"description":"Visit","quantity":1,"unitPrice":%s}],"validUntil":"%s","version":%d}
                        """.formatted(unitPrice, time.today().plusDays(7), versionOf(id))))
                .andExpect(status().isOk());
        mvc.perform(auth(post("/api/requests/" + id + "/quote/send"), token)
                        .content("{\"version\":%d}".formatted(versionOf(id))))
                .andExpect(status().isOk());
    }

    protected void accept(String reference, String phone) throws Exception {
        mvc.perform(json(post("/api/public/requests/" + reference + "/accept"))
                        .content("{\"phone\":\"%s\"}".formatted(phone)))
                .andExpect(status().isOk());
    }

    /**
     * A request that the customer accepted (ready to schedule): AC in Riyadh. Returns its id.
     * طلب وافق عليه العميل (جاهز للجدولة): مكيفات في الرياض. يرجّع رقمه.
     */
    protected long acceptedAcRequest(String token, String phone) throws Exception {
        String ref = book(acId, "RIYADH", phone);
        long id = requestId(ref);
        quoteAndSend(id, token, "300.00");
        accept(ref, phone);
        return id;
    }

    protected ResultActions schedule(long id, String token, long technicianId, LocalDate date, String time)
            throws Exception {
        return mvc.perform(auth(post("/api/requests/" + id + "/schedule"), token).content("""
                {"technicianId":%d,"date":"%s","startTime":"%s","version":%d}
                """.formatted(technicianId, date, time, versionOf(id))));
    }
}
