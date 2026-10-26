package com.masar.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import com.masar.support.IntegrationTest;

/**
 * Booking technicians: skills, city, working hours, overlaps, and running the job.
 * حجز الفنيين: المهارات، والمدينة، وساعات العمل، والتعارض، وتنفيذ الشغل.
 */
class SchedulingTest extends IntegrationTest {

    @Test
    void schedulesAnAcceptedRequestAndShowsItOnTheCalendar() throws Exception {
        String office = dispatcherToken();
        long id = acceptedAcRequest(office, "0551234567");
        schedule(id, office, userId("fahad"), tomorrow(), "09:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.technician.fullName").value("Fahad"))
                // 09:00 Riyadh is 06:00 UTC; AC takes 90 minutes. / 9 الصبح بالرياض = 6 UTC؛ والمكيفات 90 دقيقة.
                .andExpect(jsonPath("$.scheduledStart").value(tomorrow() + "T06:00:00Z"))
                .andExpect(jsonPath("$.scheduledEnd").value(tomorrow() + "T07:30:00Z"));

        mvc.perform(auth(get("/api/schedule?date=" + tomorrow() + "&city=RIYADH"), office))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technicians.length()").value(2))
                .andExpect(jsonPath("$.technicians[?(@.fullName=='Fahad')].jobs[0].id").value(contains((int) id)));
        // The customer now sees the technician and the time. / العميل الحين يشوف الفني والوقت.
        String ref = jdbc.queryForObject("select reference from service_request where id = ?", String.class, id);
        mvc.perform(json(post("/api/public/requests/track"))
                        .content("{\"reference\":\"%s\",\"phone\":\"0551234567\"}".formatted(ref)))
                .andExpect(jsonPath("$.technicianName").value("Fahad"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    void onlyAcceptedRequestsCanBeScheduled() throws Exception {
        String office = dispatcherToken();
        long id = requestId(book(acId, "RIYADH", "0551234567"));
        schedule(id, office, userId("fahad"), tomorrow(), "09:00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"))
                .andExpect(jsonPath("$.status").value("NEW"));
    }

    @Test
    void technicianMustHaveTheSkillAndWorkInTheCity() throws Exception {
        String office = dispatcherToken();
        long id = acceptedAcRequest(office, "0551234567");
        // omar does plumbing, not AC; yousef does AC but in Jeddah. / omar سباك مو مكيفات؛ و yousef مكيفات بس في جدة.
        for (String tech : new String[]{"omar", "yousef", "noura"}) {
            schedule(id, office, userId(tech), tomorrow(), "09:00")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("TECHNICIAN_NOT_QUALIFIED"));
        }
        jdbc.update("update app_user set active = false where username = 'fahad'");
        schedule(id, office, userId("fahad"), tomorrow(), "09:00")
                .andExpect(jsonPath("$.code").value("TECHNICIAN_NOT_QUALIFIED"));
    }

    @Test
    void visitsMustFitWorkingHoursOnTheHalfHourInTheFuture() throws Exception {
        String office = dispatcherToken();
        long id = acceptedAcRequest(office, "0551234567");
        long fahad = userId("fahad");
        schedule(id, office, fahad, tomorrow(), "07:30").andExpect(jsonPath("$.code").value("OUTSIDE_WORKING_HOURS"));
        // 19:00 + 90 minutes ends at 20:30, after closing. / 7 المسا + 90 دقيقة تخلص 8:30، بعد الإغلاق.
        schedule(id, office, fahad, tomorrow(), "19:00").andExpect(jsonPath("$.code").value("OUTSIDE_WORKING_HOURS"));
        schedule(id, office, fahad, time.today().minusDays(1), "10:00")
                .andExpect(jsonPath("$.code").value("OUTSIDE_WORKING_HOURS"));
        schedule(id, office, fahad, tomorrow(), "09:15")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.startTime").value("HalfHour"));
        // Exactly 18:30 + 90 = 20:00 is the last allowed visit. / 6:30 + 90 دقيقة = 8 بالضبط هي آخر زيارة مسموحة.
        schedule(id, office, fahad, tomorrow(), "18:30").andExpect(status().isOk());
    }

    @Test
    void overlappingVisitsAreRefusedButBackToBackIsFine() throws Exception {
        String office = dispatcherToken();
        long fahad = userId("fahad");
        long first = acceptedAcRequest(office, "0551111111");
        long second = acceptedAcRequest(office, "0552222222");
        long third = acceptedAcRequest(office, "0553333333");
        schedule(first, office, fahad, tomorrow(), "10:00").andExpect(status().isOk());
        String firstRef = jdbc.queryForObject("select reference from service_request where id = ?", String.class, first);
        // 10:30–12:00 overlaps 10:00–11:30. / يتداخل مع الأولى.
        schedule(second, office, fahad, tomorrow(), "10:30")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TECHNICIAN_BUSY"))
                .andExpect(jsonPath("$.reference").value(firstRef));
        // 08:30–10:00 ends exactly when the first starts. / تخلص بالضبط لما تبدأ الأولى.
        schedule(second, office, fahad, tomorrow(), "08:30").andExpect(status().isOk());
        schedule(third, office, fahad, tomorrow(), "11:30").andExpect(status().isOk());

        // Rescheduling a job does not clash with itself. / إعادة جدولة شغل ما تتعارض مع نفسه.
        schedule(first, office, fahad, tomorrow(), "10:00").andExpect(status().isOk());
        // Cancelling frees the time for someone else. / الإلغاء يفضّي الوقت لغيره.
        mvc.perform(auth(post("/api/requests/" + first + "/cancel"), office)
                        .content("{\"reason\":\"Customer away\",\"version\":%d}".formatted(versionOf(first))))
                .andExpect(status().isOk());
        schedule(second, office, fahad, tomorrow(), "10:00").andExpect(status().isOk());
    }

    @Test
    void twoDispatchersBookingTheSameSlotAtOnceOnlyOneWins() throws Exception {
        String office = dispatcherToken();
        long fahad = userId("fahad");
        List<Long> ids = List.of(acceptedAcRequest(office, "0551111111"), acceptedAcRequest(office, "0552222222"),
                acceptedAcRequest(office, "0553333333"), acceptedAcRequest(office, "0554444444"));
        // All four start together, each for a different request but the same technician and time.
        // الأربعة يبدون مع بعض، كل واحد لطلب مختلف لكن نفس الفني ونفس الوقت.
        CountDownLatch go = new CountDownLatch(1);
        List<Callable<Integer>> calls = new ArrayList<>();
        for (long id : ids) {
            long version = versionOf(id);
            calls.add(() -> {
                go.await();
                return mvc.perform(auth(post("/api/requests/" + id + "/schedule"), office).content("""
                                {"technicianId":%d,"date":"%s","startTime":"14:00","version":%d}
                                """.formatted(fahad, tomorrow(), version)))
                        .andReturn().getResponse().getStatus();
            });
        }
        ExecutorService pool = Executors.newFixedThreadPool(ids.size());
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (Callable<Integer> c : calls) {
                results.add(pool.submit(c));
            }
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : results) {
                statuses.add(f.get());
            }
            assertThat(statuses).containsOnly(200, 409);
            assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("select count(*) from service_request where status = 'SCHEDULED'", Long.class))
                .isEqualTo(1);
    }

    @Test
    void technicianStartsAndCompletesTheirJob() throws Exception {
        String office = dispatcherToken();
        long id = acceptedAcRequest(office, "0551234567");
        schedule(id, office, userId("fahad"), tomorrow(), "09:00").andExpect(status().isOk());
        String fahad = technicianToken("fahad");

        mvc.perform(auth(get("/api/jobs/mine"), fahad))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].phone").value("966551234567"));
        mvc.perform(auth(get("/api/jobs/mine"), technicianToken("omar"))).andExpect(jsonPath("$.length()").value(0));

        // Completing before starting is not a valid move. / الإنهاء قبل البدء انتقال غير صحيح.
        mvc.perform(auth(post("/api/requests/" + id + "/complete"), fahad)
                        .content("{\"version\":%d}".formatted(versionOf(id))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"));
        mvc.perform(auth(post("/api/requests/" + id + "/start"), fahad)
                        .content("{\"version\":%d}".formatted(versionOf(id))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mvc.perform(auth(post("/api/requests/" + id + "/complete"), fahad)
                        .content("{\"note\":\"Cleaned both units\",\"version\":%d}".formatted(versionOf(id))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completionNote").value("Cleaned both units"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
        // Finished today, so it is still on the list. / خلص اليوم، فلسا في القائمة.
        mvc.perform(auth(get("/api/jobs/mine"), fahad)).andExpect(jsonPath("$[0].status").value("COMPLETED"));
        assertThat(jdbc.queryForList("select actor from request_event where request_id = ? order by id",
                String.class, id)).endsWith("fahad", "fahad");
    }
}
