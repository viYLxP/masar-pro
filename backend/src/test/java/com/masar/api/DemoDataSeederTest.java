package com.masar.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.context.TestPropertySource;

import com.masar.config.DemoDataSeeder;
import com.masar.support.IntegrationTest;

/**
 * The demo data must itself follow every rule, or the demo would show impossible states.
 * البيانات التجريبية نفسها لازم تتبع كل القواعد، وإلا العرض بيبين حالات مستحيلة.
 */
@TestPropertySource(properties = {"masar.seed.enabled=true", "masar.seed.admin-password=Admin@12345",
        "masar.seed.staff-password=Staff@12345"})
class DemoDataSeederTest extends IntegrationTest {

    @Autowired
    private DemoDataSeeder seeder;

    @Test
    void seedsEveryStatusConsistently() throws Exception {
        // The base class already created users, so clear them to let the seeder run like on a fresh database.
        // الكلاس الأساسي أنشأ مستخدمين، فنمسحهم حتى يشتغل الـ seeder كأنها قاعدة بيانات جديدة.
        jdbc.execute("truncate table request_event, quote_line, quote, service_request, technician_skill, "
                + "service_type, app_user restart identity cascade");
        seeder.run(new DefaultApplicationArguments());

        Map<String, Long> counts = new java.util.HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList("select status, count(*) c from service_request group by status")) {
            counts.put((String) row.get("status"), ((Number) row.get("c")).longValue());
        }
        assertThat(counts).containsEntry("NEW", 3L).containsEntry("QUOTED", 3L).containsEntry("ACCEPTED", 1L)
                .containsEntry("SCHEDULED", 2L).containsEntry("IN_PROGRESS", 1L).containsEntry("COMPLETED", 3L)
                .containsEntry("REJECTED", 1L).containsEntry("CANCELLED", 1L);

        // Every history starts with NEW and the latest event matches the current status.
        // كل سجل يبدأ بـ NEW وآخر حدث يطابق الحالة الحالية.
        List<Map<String, Object>> broken = jdbc.queryForList("""
                select r.reference from service_request r
                where (select e.to_status from request_event e where e.request_id = r.id order by e.created_at, e.id limit 1) <> 'NEW'
                   or (select e.to_status from request_event e where e.request_id = r.id order by e.created_at desc, e.id desc limit 1) <> r.status
                   or r.created_at > r.updated_at
                """);
        assertThat(broken).isEmpty();
        // Finished jobs are in the past and their window matches the service duration.
        // الأعمال المنتهية في الماضي ووقتها يطابق مدة الخدمة.
        assertThat(jdbc.queryForObject("""
                select count(*) from service_request r join service_type s on s.id = r.service_type_id
                where r.status = 'COMPLETED' and (r.completed_at > now() or r.scheduled_start > now()
                   or r.scheduled_end - r.scheduled_start <> make_interval(mins => s.duration_minutes))
                """, Long.class)).isZero();
        // The draft quote of a NEW request is not visible as sent. / مسودة طلب NEW ما تظهر كمرسلة.
        assertThat(jdbc.queryForObject("""
                select count(*) from quote q join service_request r on r.id = q.request_id
                where r.status = 'NEW' and q.sent_at is not null""", Long.class)).isZero();

        // The seeded in-progress job lands on yesterday evening when the seed runs before 08:00 Riyadh time,
        // so the expected "today" count is read from the data instead of being fixed.
        // شغل "قيد التنفيذ" المزروع يصير مساء أمس لو الزرع صار قبل 8 الصبح بتوقيت الرياض،
        // فعدد "اليوم" المتوقع ينقرأ من البيانات بدل ما يكون رقم ثابت.
        Long todayJobs = jdbc.queryForObject("""
                select count(*) from service_request
                where status in ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED')
                  and (scheduled_start at time zone 'Asia/Riyadh')::date = (now() at time zone 'Asia/Riyadh')::date
                """, Long.class);
        assertThat(todayJobs).isBetween(0L, 1L);

        // Seeded payments: 2 cash, 2 card, each on a request marked PAID; the customer accounts own 3 requests.
        // المدفوعات المزروعة: 2 كاش و2 بطاقة، كل وحدة على طلب معلّم PAID؛ وحسابات العملاء عندها 3 طلبات.
        assertThat(jdbc.queryForObject("select count(*) from payment where status = 'PAID' and method = 'CASH'",
                Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from payment where status = 'PAID' and method = 'CARD'",
                Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                select count(*) from service_request r where r.payment_status = 'PAID'
                  and exists (select 1 from payment p where p.request_id = r.id and p.status = 'PAID'
                              and p.amount = (select q.total from quote q where q.request_id = r.id))
                """, Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from payment where paid_at > now()", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from service_request where customer_id is not null",
                Long.class)).isEqualTo(3);
        login("0553001010", "Customer@12345");

        String token = login("noura", "Staff@12345");
        mvc.perform(auth(get("/api/dashboard"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptanceRate").isNumber())
                .andExpect(jsonPath("$.today.length()").value(todayJobs.intValue()));
        login("fahad", "Staff@12345");
    }
}
