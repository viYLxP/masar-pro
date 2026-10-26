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
        assertThat(counts).containsEntry("NEW", 3L).containsEntry("QUOTED", 2L).containsEntry("ACCEPTED", 1L)
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

        String token = login("noura", "Staff@12345");
        mvc.perform(auth(get("/api/dashboard"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptanceRate").isNumber())
                .andExpect(jsonPath("$.today.length()").value(1));
        login("fahad", "Staff@12345");
    }
}
