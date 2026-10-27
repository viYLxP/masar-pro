package com.masar.request;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masar.catalog.ServiceTypeDtos.ServiceRef;
import com.masar.common.ApiException;
import com.masar.common.BusinessTime;
import com.masar.common.City;
import com.masar.common.ErrorCode;
import com.masar.common.Text;
import com.masar.common.Versions;
import com.masar.payment.PaymentService;
import com.masar.request.RequestDtos.DayBoard;
import com.masar.request.RequestDtos.JobCard;
import com.masar.request.RequestDtos.JobSlot;
import com.masar.request.RequestDtos.RequestDetail;
import com.masar.request.RequestDtos.ScheduleRequest;
import com.masar.request.RequestDtos.TechnicianDay;
import com.masar.user.AppUser;
import com.masar.user.AppUserRepository;
import com.masar.user.CurrentUserService;
import com.masar.user.Role;

/**
 * Booking technicians and running the job: schedule, start, complete, and the calendar views.
 * حجز الفنيين وتنفيذ الشغل: الجدولة، البدء، الإنهاء، وعروض الجدول.
 */
@Service
public class SchedulingService {

    // Jobs that appear on the calendar, including finished ones (so a day's work stays visible).
    // الأعمال اللي تظهر في الجدول، بما فيها المنتهية (حتى يبقى شغل اليوم ظاهر).
    private static final EnumSet<RequestStatus> ON_CALENDAR =
            EnumSet.of(RequestStatus.SCHEDULED, RequestStatus.IN_PROGRESS, RequestStatus.COMPLETED);

    private final ServiceRequestRepository requests;
    private final AppUserRepository users;
    private final RequestService requestService;
    private final RequestHistory history;
    private final CurrentUserService currentUser;
    private final BusinessTime time;
    private final PaymentService payments;

    public SchedulingService(ServiceRequestRepository requests, AppUserRepository users,
                             RequestService requestService, RequestHistory history, CurrentUserService currentUser,
                             BusinessTime time,
                             PaymentService payments) {
        this.payments = payments;
        this.requests = requests;
        this.users = users;
        this.requestService = requestService;
        this.history = history;
        this.currentUser = currentUser;
        this.time = time;
    }

    /**
     * Books (or re-books) a technician for an accepted request.
     * يحجز (أو يعيد حجز) فني لطلب موافق عليه.
     *
     * Checks, in order / الفحوصات بالترتيب:
     * 1. the request is ACCEPTED or already SCHEDULED / الطلب ACCEPTED أو مجدول من قبل
     * 2. the technician offers this service in this city / الفني يقدّم هذي الخدمة في هذي المدينة
     * 3. the visit is in the future, on the half hour, inside 08:00–20:00 / الزيارة في المستقبل، على نص الساعة، داخل 8 الصبح لين 8 الليل
     * 4. the technician has nothing else at that time / الفني ما عنده شي ثاني بنفس الوقت
     */
    @Transactional
    public RequestDetail schedule(Long id, ScheduleRequest body) {
        ServiceRequest request = requestService.find(id);
        Versions.check(request.getVersion(), body.version());
        request.requireStatus(RequestStatus.ACCEPTED, RequestStatus.SCHEDULED);

        // Lock the technician first: concurrent bookings of the same person then queue up here,
        // and the overlap check below always sees the booking that committed just before.
        // نقفل الفني أول: الحجوزات المتزامنة لنفس الشخص تنتظر بالدور هنا،
        // وفحص التعارض تحت دائمًا يشوف الحجز اللي انحفظ قبله.
        AppUser technician = users.lockById(body.technicianId())
                .orElseThrow(() -> ApiException.notFound("user", body.technicianId()));
        if (!technician.canServe(request.getServiceType(), request.getCity())) {
            throw new ApiException(ErrorCode.TECHNICIAN_NOT_QUALIFIED);
        }

        LocalTime startTime = body.startTime();
        if (startTime.getMinute() % 30 != 0 || startTime.getSecond() != 0 || startTime.getNano() != 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("startTime", "HalfHour")));
        }
        Instant start = time.at(body.date(), startTime);
        Instant end = start.plusSeconds(request.getServiceType().getDurationMinutes() * 60L);
        LocalTime endTime = time.timeOf(end);
        boolean sameDay = time.dateOf(end).equals(body.date()) || endTime.equals(LocalTime.MIDNIGHT);
        if (!start.isAfter(time.now()) || startTime.isBefore(TimeSlot.DAY_START)
                || !sameDay || endTime.isAfter(TimeSlot.DAY_END)) {
            throw new ApiException(ErrorCode.OUTSIDE_WORKING_HOURS);
        }

        List<ServiceRequest> clashes = requests.overlapping(technician.getId(), RequestStatus.BOOKED, request.getId(),
                start, end);
        if (!clashes.isEmpty()) {
            ServiceRequest clash = clashes.getFirst();
            throw new ApiException(ErrorCode.TECHNICIAN_BUSY, Map.of(
                    "reference", clash.getReference(),
                    "start", clash.getScheduledStart().toString(),
                    "end", clash.getScheduledEnd().toString()));
        }

        RequestStatus from = request.moveTo(RequestStatus.SCHEDULED);
        request.book(technician, start, end);
        history.staff(request, from, technician.getFullName(), time.now());
        return requestService.detail(request);
    }

    /**
     * The technician arrives: SCHEDULED → IN_PROGRESS. Only the assigned technician or the office may do it.
     * الفني وصل: SCHEDULED ← IN_PROGRESS. بس الفني المعيّن أو المكتب يقدر يسويها.
     */
    @Transactional
    public RequestDetail start(Long id, Long version) {
        ServiceRequest request = requestService.find(id);
        requestService.requireAccess(request);
        Versions.check(request.getVersion(), version);
        RequestStatus from = request.moveTo(RequestStatus.IN_PROGRESS);
        request.setStartedAt(time.now());
        history.staff(request, from, null, time.now());
        return requestService.detail(request);
    }

    @Transactional
    public RequestDetail complete(Long id, String note, boolean cashCollected, Long version) {
        ServiceRequest request = requestService.find(id);
        requestService.requireAccess(request);
        Versions.check(request.getVersion(), version);
        RequestStatus from = request.moveTo(RequestStatus.COMPLETED);
        request.setCompletedAt(time.now());
        request.setCompletionNote(Text.blankToNull(note));
        history.staff(request, from, note, time.now());
        // Ticked on the completion form when the customer paid the technician in cash.
        // ينختار في نموذج الإنهاء لما العميل يدفع للفني كاش.
        if (cashCollected) {
            payments.recordCash(request);
        }
        return requestService.detail(request);
    }

    /**
     * The signed-in technician's open jobs, plus anything they finished today.
     * أعمال الفني المسجّل المفتوحة، مع اللي خلّصه اليوم.
     */
    @Transactional(readOnly = true)
    public List<JobCard> myJobs() {
        AppUser me = currentUser.require();
        return requests.jobsOf(me.getId(), RequestStatus.BOOKED, RequestStatus.COMPLETED, time.startOf(time.today()))
                .stream().map(JobCard::from).toList();
    }

    /**
     * One day of the calendar: each technician (optionally only those who fit a city and service) with their jobs.
     * يوم واحد من الجدول: كل فني (واختياريًا بس اللي يناسبون مدينة وخدمة) مع أعماله.
     */
    @Transactional(readOnly = true)
    public DayBoard day(LocalDate date, City city, Long serviceTypeId) {
        Map<Long, List<JobSlot>> jobsByTechnician = requests
                .onCalendar(ON_CALENDAR, time.startOf(date), time.startOf(date.plusDays(1)))
                .stream()
                .collect(Collectors.groupingBy(r -> r.getTechnician().getId(),
                        Collectors.mapping(JobSlot::from, Collectors.toList())));
        List<TechnicianDay> technicians = users.findByRoleAndActiveTrueOrderByFullName(Role.TECHNICIAN).stream()
                .filter(u -> city == null || u.getCity() == city)
                .filter(u -> serviceTypeId == null
                        || u.getSkills().stream().anyMatch(s -> s.getId().equals(serviceTypeId)))
                .map(u -> new TechnicianDay(u.getId(), u.getFullName(), u.getCity(),
                        u.getSkills().stream().map(ServiceRef::of).toList(),
                        jobsByTechnician.getOrDefault(u.getId(), List.of())))
                .toList();
        return new DayBoard(date, technicians);
    }
}
