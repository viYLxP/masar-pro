package com.masar.config;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.masar.catalog.ServiceType;
import com.masar.catalog.ServiceTypeRepository;
import com.masar.common.BusinessTime;
import com.masar.common.City;
import com.masar.request.BookingService;
import com.masar.request.PropertyType;
import com.masar.request.RequestDtos.BookingRequest;
import com.masar.request.RequestDtos.QuoteLineRequest;
import com.masar.request.RequestDtos.QuoteRequest;
import com.masar.request.RequestDtos.ScheduleRequest;
import com.masar.request.RequestService;
import com.masar.request.SchedulingService;
import com.masar.request.ServiceRequest;
import com.masar.request.ServiceRequestRepository;
import com.masar.request.TimeSlot;
import com.masar.user.AppUser;
import com.masar.user.AppUserRepository;
import com.masar.user.Role;

/**
 * Fills an EMPTY database with demo staff, the service catalogue and requests in every status on first start.
 * يعبّي قاعدة بيانات فاضية بموظفين تجريبيين وقائمة الخدمات وطلبات بكل الحالات عند أول تشغيل.
 *
 * <p>Requests go through the real services (booking, quote, accept, schedule, start, complete), so demo data obeys
 * the same rules as real data: references, VAT, skills, overlap checks and history. Plain SQL is only used afterwards
 * to move finished jobs into the past, so the dashboard has a believable month. It never runs when users already
 * exist, and can be turned off with SEED_DEMO_DATA=false.</p>
 * <p>الطلبات تمر عبر الخدمات الحقيقية (الحجز، العرض، القبول، الجدولة، البدء، الإنهاء)، فالبيانات التجريبية تخضع
 * لنفس قواعد البيانات الحقيقية: الأرقام المرجعية والضريبة والمهارات وفحص التعارض والسجل. والـ SQL المباشر يُستخدم
 * بعدها بس لإرجاع الأعمال المنتهية للماضي، حتى يكون للوحة شهر منطقي. وما يشتغل أبدًا لو فيه مستخدمين،
 * ويمكن إيقافه بـ SEED_DEMO_DATA=false.</p>
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final AppProperties properties;
    private final AppUserRepository users;
    private final ServiceTypeRepository serviceTypes;
    private final PasswordEncoder passwordEncoder;
    private final BookingService bookings;
    private final RequestService requests;
    private final SchedulingService scheduling;
    private final ServiceRequestRepository requestRepository;
    private final BusinessTime time;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    private LocalDate today;

    public DemoDataSeeder(AppProperties properties, AppUserRepository users, ServiceTypeRepository serviceTypes,
                          PasswordEncoder passwordEncoder, BookingService bookings, RequestService requests,
                          SchedulingService scheduling, ServiceRequestRepository requestRepository,
                          BusinessTime time, JdbcTemplate jdbc, TransactionTemplate tx) {
        this.properties = properties;
        this.users = users;
        this.serviceTypes = serviceTypes;
        this.passwordEncoder = passwordEncoder;
        this.bookings = bookings;
        this.requests = requests;
        this.scheduling = scheduling;
        this.requestRepository = requestRepository;
        this.time = time;
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.seed().enabled() || users.count() > 0) {
            return;
        }
        // One transaction for the whole seed: either all demo data is created, or none.
        // معاملة وحدة لكل البيانات: يا تنضاف كلها يا ولا شي.
        tx.executeWithoutResult(status -> seed());
        log.info("Demo data created. Log in as 'admin', 'noura' (dispatcher) or 'fahad' (technician); "
                + "passwords from ADMIN_PASSWORD / STAFF_PASSWORD.");
    }

    private void seed() {
        today = time.today();
        ServiceType ac = service("AC", 1, "صيانة وتنظيف المكيفات", "AC maintenance & cleaning",
                "تنظيف وفحص وتعبئة فريون للمكيفات السبليت والشباك والمركزية.",
                "Cleaning, check-up and refrigerant top-up for split, window and central units.", "150.00", 90);
        ServiceType cleaning = service("CLEANING", 2, "تنظيف المنازل والمكاتب", "Home & office cleaning",
                "تنظيف شامل بفريق مدرّب ومواد آمنة.", "Deep cleaning by a trained team with safe products.",
                "350.00", 240);
        ServiceType plumbing = service("PLUMBING", 3, "سباكة", "Plumbing",
                "كشف وإصلاح التسربات وتركيب الأدوات الصحية والسخانات.",
                "Leak detection and repair, fixtures and water heaters.", "120.00", 60);
        ServiceType electrical = service("ELECTRICAL", 4, "كهرباء", "Electrical",
                "إصلاح الأعطال وتمديد النقاط وتركيب الإنارة.", "Fault repair, new sockets and lighting.",
                "120.00", 60);
        ServiceType pest = service("PEST", 5, "مكافحة الحشرات", "Pest control",
                "رش ومكافحة بمواد مرخصة من الجهات المختصة.", "Treatment with officially licensed products.",
                "250.00", 120);
        ServiceType moving = service("MOVING", 6, "نقل الأثاث", "Furniture moving",
                "فك وتغليف ونقل وتركيب الأثاث داخل المدينة.",
                "Dismantling, packing, moving and reassembly within the city.", "800.00", 300);

        String staffPassword = passwordEncoder.encode(properties.seed().staffPassword());
        users.save(new AppUser("admin", "System Admin",
                passwordEncoder.encode(properties.seed().adminPassword()), Role.ADMIN));
        AppUser noura = new AppUser("noura", "Noura Al-Harbi", staffPassword, Role.DISPATCHER);
        noura.setPhone("966550000010");
        users.save(noura);
        AppUser fahad = technician("fahad", "Fahad Al-Qahtani", "966551000001", City.RIYADH, staffPassword, ac, electrical);
        AppUser omar = technician("omar", "Omar Al-Shammari", "966551000002", City.RIYADH, staffPassword, plumbing, electrical);
        AppUser saeed = technician("saeed", "Saeed Al-Malki", "966551000003", City.RIYADH, staffPassword, cleaning, pest);
        AppUser yousef = technician("yousef", "Yousef Al-Zahrani", "966551000004", City.JEDDAH, staffPassword, ac, plumbing, cleaning);
        AppUser majed = technician("majed", "Majed Al-Dossary", "966551000005", City.DAMMAM, staffPassword, ac, electrical, moving);

        // Office actions run as the dispatcher, so the history shows "noura" like it would in real use.
        // عمليات المكتب تشتغل باسم الموزّعة، فالسجل يبين "noura" مثل الاستخدام الحقيقي.
        asUser("noura", () -> {
            seedRequests(ac, cleaning, plumbing, electrical, pest, moving, fahad, omar, saeed, yousef, majed);
            return null;
        });
    }

    private void seedRequests(ServiceType ac, ServiceType cleaning, ServiceType plumbing, ServiceType electrical,
                              ServiceType pest, ServiceType moving, AppUser fahad, AppUser omar, AppUser saeed,
                              AppUser yousef, AppUser majed) {
        // ----- Finished jobs first, booked on future days, then moved into the past by SQL -----
        // ----- الأعمال المنتهية أول، تنحجز في أيام قادمة، وبعدها ترجع للماضي بالـ SQL -----
        ServiceRequest hind = book("Hind Al-Rashid", "0553001001", City.RIYADH, "Al Aqiq", PropertyType.VILLA, ac,
                3, TimeSlot.MORNING, "Two split units in the majlis are noisy.");
        quoteAndAccept(hind, line("Split AC deep clean", 2, "150.00"), line("Refrigerant top-up", 1, "180.00"));
        finish(hind, fahad, 3, LocalTime.of(9, 0), "Both units cleaned, one needed refrigerant. Working well.");
        ServiceRequest sultan = book("سلطان المطيري", "0553001002", City.JEDDAH, "الشاطئ", PropertyType.APARTMENT, ac,
                4, TimeSlot.AFTERNOON, "المكيف ما يبرد زين.");
        quoteAndAccept(sultan, line("تنظيف مكيف سبليت", 3, "140.00"));
        finish(sultan, yousef, 4, LocalTime.of(13, 0), "تم تنظيف 3 وحدات.");
        ServiceRequest faris = book("Faris Al-Otaibi", "0553001003", City.DAMMAM, "Al Faisaliyah", PropertyType.OFFICE,
                electrical, 5, TimeSlot.MORNING, "Several sockets in the office stopped working.");
        quoteAndAccept(faris, line("Fault finding", 1, "120.00"), line("Replace socket", 4, "45.00"));
        finish(faris, majed, 5, LocalTime.of(10, 0), "Burnt breaker replaced, 4 sockets renewed.");

        // ----- Open requests in every status / طلبات مفتوحة بكل الحالات -----
        book("عبدالله السبيعي", "0553001004", City.RIYADH, "الملقا", PropertyType.VILLA, ac, 1, TimeSlot.MORNING,
                "3 مكيفات سبليت في الدور الثاني ما تبرد.");
        book("Sarah Williams", "0553001005", City.JEDDAH, "Al Rawdah", PropertyType.APARTMENT, cleaning, 2,
                TimeSlot.AFTERNOON, "Move-out cleaning, 3 bedrooms.");
        ServiceRequest munira = book("منيرة العتيبي", "0553001006", City.RIYADH, "النرجس", PropertyType.VILLA, pest, 3,
                TimeSlot.EVENING, "صراصير في المطبخ والحديقة.");
        // A draft quote that is not sent yet: the customer must not see it on the tracking page.
        // عرض مسودة ما انرسل: العميل ما لازم يشوفه في صفحة التتبع.
        saveQuote(munira, 7, line("Indoor treatment", 1, "250.00"), line("Garden treatment", 1, "200.00"));

        ServiceRequest khalid = book("Khalid Mansour", "0553001007", City.RIYADH, "Al Yasmin", PropertyType.APARTMENT,
                plumbing, 2, TimeSlot.MORNING, "Water heater leaking.");
        // Valid until tomorrow, so it shows under "quotes about to expire".
        // صالح لين بكرة، فيظهر تحت "عروض بتنتهي قريب".
        saveQuote(khalid, 1, line("Leak repair", 1, "120.00"), line("Water heater valve", 1, "85.00"));
        requests.sendQuote(khalid.getId(), version(khalid));
        ServiceRequest noraD = book("نورة الدوسري", "0553001008", City.DAMMAM, "الفيصلية", PropertyType.VILLA, moving, 10,
                TimeSlot.MORNING, "نقل أثاث فيلا كاملة لحي الشاطئ.");
        saveQuote(noraD, 7, line("نقل أثاث فيلا (فريق 4 أشخاص)", 1, "2400.00"), line("تغليف", 1, "350.00"));
        requests.sendQuote(noraD.getId(), version(noraD));

        ServiceRequest ahmed = book("Ahmed Al-Harbi", "0553001009", City.RIYADH, "Hittin", PropertyType.VILLA,
                electrical, 2, TimeSlot.EVENING, "Garden lights and two outdoor sockets.");
        quoteAndAccept(ahmed, line("Garden lighting installation", 6, "60.00"), line("Outdoor socket", 2, "75.00"));

        ServiceRequest reem = book("ريم القحطاني", "0553001010", City.RIYADH, "الصحافة", PropertyType.APARTMENT, ac, 1,
                TimeSlot.MORNING, "تنظيف مكيفين قبل الشتاء.");
        quoteAndAccept(reem, line("تنظيف مكيف سبليت", 2, "150.00"));
        schedule(reem, fahad, 1, LocalTime.of(9, 0));
        ServiceRequest mohammed = book("Mohammed Al-Ghamdi", "0553001011", City.RIYADH, "Al Wurud", PropertyType.VILLA,
                cleaning, 2, TimeSlot.AFTERNOON, "Deep clean before guests arrive.");
        quoteAndAccept(mohammed, line("Villa deep cleaning (team of 3)", 1, "950.00"));
        schedule(mohammed, saeed, 2, LocalTime.of(13, 0));

        // On site right now: booked for tomorrow, started, then moved to this morning by SQL.
        // في الموقع الحين: محجوز لبكرة، بدأ، وبعدها ينقل لهذا الصباح بالـ SQL.
        ServiceRequest faisal = book("فيصل الشهري", "0553001012", City.RIYADH, "العليا", PropertyType.OFFICE, plumbing, 1,
                TimeSlot.MORNING, "انسداد في مغاسل المكتب.");
        quoteAndAccept(faisal, line("فتح انسداد", 2, "110.00"));
        schedule(faisal, omar, 1, LocalTime.of(11, 0));
        scheduling.start(faisal.getId(), version(faisal));

        ServiceRequest laila = book("Laila Hassan", "0553001013", City.RIYADH, "Al Nakheel", PropertyType.APARTMENT,
                pest, 3, TimeSlot.MORNING, "Bed bugs in two rooms.");
        saveQuote(laila, 7, line("Bed bug treatment (2 rooms)", 1, "600.00"));
        requests.sendQuote(laila.getId(), version(laila));
        bookings.reject(laila.getReference(), laila.getPhone(), "Found a cheaper offer.");

        ServiceRequest turki = book("تركي العنزي", "0553001014", City.RIYADH, "قرطبة", PropertyType.VILLA, pest, 4,
                TimeSlot.EVENING, "رش دوري للفيلا.");
        requests.cancel(turki.getId(), "العميل مسافر وبيحجز بعد رجوعه.", version(turki));

        requestRepository.flush();

        // ----- Move timestamps into the past / إرجاع التواريخ للماضي -----
        retime(hind, ago(9, 10, 20), ago(9, 14, 5), ago(8, 11, 30), ago(8, 12, 0), ago(3, 9, 5), ago(3, 10, 40));
        retime(sultan, ago(12, 18, 0), ago(11, 9, 30), ago(11, 20, 15), ago(10, 9, 0), ago(6, 13, 10), ago(6, 15, 0));
        retime(faris, ago(41, 8, 45), ago(40, 10, 0), ago(39, 16, 30), ago(39, 17, 0), ago(35, 10, 0), ago(35, 11, 5));
        retime(faisal, ago(2, 10, 15), ago(2, 13, 0), ago(1, 9, 40), ago(1, 10, 5),
                time.now().minus(Duration.ofMinutes(35)));
        retime(laila, ago(5, 11, 0), ago(4, 12, 30), ago(3, 18, 20));
        retime(turki, ago(4, 15, 0), ago(2, 11, 45));
        alignJobTimes();
    }

    // ----- Building blocks / أدوات البناء -----

    private ServiceType service(String code, int order, String nameAr, String nameEn, String descAr, String descEn,
                                String price, int minutes) {
        ServiceType s = new ServiceType(code);
        s.setSortOrder(order);
        s.setNameAr(nameAr);
        s.setNameEn(nameEn);
        s.setDescriptionAr(descAr);
        s.setDescriptionEn(descEn);
        s.setBasePrice(new BigDecimal(price));
        s.setDurationMinutes(minutes);
        return serviceTypes.save(s);
    }

    private AppUser technician(String username, String name, String phone, City city, String passwordHash,
                               ServiceType... skills) {
        AppUser u = new AppUser(username, name, passwordHash, Role.TECHNICIAN);
        u.setPhone(phone);
        u.setCity(city);
        u.getSkills().addAll(Set.of(skills));
        return users.save(u);
    }

    // Booked through the public booking path, so the history says "customer". / الحجز عبر المسار العام، فالسجل يقول "customer".
    private ServiceRequest book(String name, String phone, City city, String district, PropertyType property,
                                ServiceType service, int daysAhead, TimeSlot slot, String details) {
        String reference = bookings.create(new BookingRequest(name, phone, null, city, district, null, property,
                service.getId(), today.plusDays(daysAhead), slot, details, null), false).reference();
        return requestRepository.findByReference(reference).orElseThrow();
    }

    private void saveQuote(ServiceRequest r, int validDays, QuoteLineRequest... lines) {
        requests.saveQuote(r.getId(), new QuoteRequest(List.of(lines), today.plusDays(validDays), null, version(r)));
    }

    private void quoteAndAccept(ServiceRequest r, QuoteLineRequest... lines) {
        saveQuote(r, 7, lines);
        requests.sendQuote(r.getId(), version(r));
        bookings.accept(r.getReference(), r.getPhone());
    }

    private void schedule(ServiceRequest r, AppUser technician, int daysAhead, LocalTime start) {
        scheduling.schedule(r.getId(), new ScheduleRequest(technician.getId(), today.plusDays(daysAhead), start,
                version(r)));
    }

    private void finish(ServiceRequest r, AppUser technician, int daysAhead, LocalTime start, String note) {
        schedule(r, technician, daysAhead, start);
        scheduling.start(r.getId(), version(r));
        scheduling.complete(r.getId(), note, version(r));
    }

    private static QuoteLineRequest line(String description, int quantity, String price) {
        return new QuoteLineRequest(description, quantity, new BigDecimal(price));
    }

    // The current version straight from the table (the services keep changing it). / رقم النسخة من الجدول مباشرة.
    private Long version(ServiceRequest r) {
        requestRepository.flush();
        return jdbc.queryForObject("select version from service_request where id = ?", Long.class, r.getId());
    }

    private Instant ago(int days, int hour, int minute) {
        return time.at(today.minusDays(days), LocalTime.of(hour, minute));
    }

    /**
     * Gives the request's history events these times (oldest first) and sets created/updated to the first/last one.
     * يعطي أحداث سجل الطلب هالأوقات (الأقدم أول) ويخلي وقت الإنشاء والتحديث أول وآخر وقت.
     */
    private void retime(ServiceRequest r, Instant... times) {
        List<Long> eventIds = jdbc.queryForList(
                "select id from request_event where request_id = ? order by id", Long.class, r.getId());
        List<Instant> list = new ArrayList<>(List.of(times));
        for (int i = 0; i < eventIds.size() && i < list.size(); i++) {
            jdbc.update("update request_event set created_at = ? where id = ?",
                    Timestamp.from(list.get(i)), eventIds.get(i));
        }
        jdbc.update("update service_request set created_at = ?, updated_at = ? where id = ?",
                Timestamp.from(list.getFirst()), Timestamp.from(list.getLast()), r.getId());
    }

    /**
     * Derives the other timestamps from the (moved) history, so every date on a request agrees with its timeline.
     * يشتق باقي الأوقات من السجل (بعد النقل)، حتى كل تاريخ في الطلب يتوافق مع تسلسله الزمني.
     */
    private void alignJobTimes() {
        String zone = time.zone().getId();
        jdbc.update("""
                update quote q set
                  sent_at = (select max(e.created_at) from request_event e
                             where e.request_id = q.request_id and e.to_status = 'QUOTED'),
                  responded_at = (select max(e.created_at) from request_event e
                                  where e.request_id = q.request_id and e.to_status in ('ACCEPTED', 'REJECTED'))
                where q.sent_at is not null
                """);
        jdbc.update("""
                update quote q set valid_until = cast((q.sent_at at time zone ?) as date) + 7,
                                   created_at = q.sent_at, updated_at = q.sent_at
                where q.request_id in (select r.id from service_request r
                                       where r.status in ('COMPLETED', 'IN_PROGRESS', 'REJECTED'))
                """, zone);
        jdbc.update("""
                update service_request r set
                  started_at = (select max(e.created_at) from request_event e
                                where e.request_id = r.id and e.to_status = 'IN_PROGRESS'),
                  completed_at = (select max(e.created_at) from request_event e
                                  where e.request_id = r.id and e.to_status = 'COMPLETED')
                where r.status in ('IN_PROGRESS', 'COMPLETED')
                """);
        // The visit window starts at the half hour before the technician started, and lasts the service's duration.
        // وقت الزيارة يبدأ من نص الساعة اللي قبل ما يبدأ الفني، ويستمر مدة الخدمة.
        jdbc.update("""
                update service_request r set
                  scheduled_start = date_trunc('hour', r.started_at)
                      + case when extract(minute from r.started_at) >= 30 then interval '30 minutes' else interval '0' end,
                  scheduled_end = date_trunc('hour', r.started_at)
                      + case when extract(minute from r.started_at) >= 30 then interval '30 minutes' else interval '0' end
                      + make_interval(mins => (select s.duration_minutes from service_type s where s.id = r.service_type_id))
                where r.status in ('IN_PROGRESS', 'COMPLETED')
                """);
        jdbc.update("""
                update service_request r set preferred_date = cast((r.scheduled_start at time zone ?) as date)
                where r.status in ('IN_PROGRESS', 'COMPLETED')
                """, zone);
    }

    /**
     * Runs the block as the given user, because the services read "who is acting" from the security context.
     * ينفّذ الكود باسم المستخدم المعطى، لأن الخدمات تقرأ "مين ينفّذ" من سياق الأمان.
     */
    private <T> T asUser(String username, Supplier<T> block) {
        var previous = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of()));
        try {
            return block.get();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }
}
