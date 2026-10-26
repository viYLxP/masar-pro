package com.masar.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masar.catalog.ServiceType;
import com.masar.catalog.ServiceTypeDtos.ServiceRef;
import com.masar.catalog.ServiceTypeRepository;
import com.masar.common.BusinessTime;
import com.masar.dashboard.DashboardDtos.DashboardResponse;
import com.masar.dashboard.DashboardDtos.RequestRow;
import com.masar.dashboard.DashboardDtos.ServiceCount;
import com.masar.request.RequestDtos.JobSlot;
import com.masar.request.RequestEventRepository;
import com.masar.request.RequestStatus;
import com.masar.request.ServiceRequest;
import com.masar.request.ServiceRequestRepository;

/**
 * Builds the office dashboard. Every number comes from the database; nothing is estimated.
 * يبني لوحة تحكم المكتب. كل رقم من قاعدة البيانات، وما فيه أي رقم تقديري.
 */
@Service
public class DashboardService {

    static final int LIST_SIZE = 6;
    // A quote shows as "expiring" when it runs out within this many days. / العرض يظهر "بينتهي" لو باقي له هالأيام.
    static final int EXPIRING_DAYS = 2;

    private final ServiceRequestRepository requests;
    private final RequestEventRepository events;
    private final ServiceTypeRepository services;
    private final BusinessTime time;

    public DashboardService(ServiceRequestRepository requests, RequestEventRepository events,
                            ServiceTypeRepository services, BusinessTime time) {
        this.requests = requests;
        this.events = events;
        this.services = services;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public DashboardResponse build() {
        LocalDate today = time.today();
        LocalDate monthStart = today.withDayOfMonth(1);

        // Every status is present (0 when empty), so the UI never has to guess a missing key.
        // كل الحالات موجودة (0 لو فاضية)، فالواجهة ما تحتاج تخمّن مفتاح ناقص.
        Map<RequestStatus, Long> counts = new EnumMap<>(RequestStatus.class);
        for (RequestStatus s : RequestStatus.values()) {
            counts.put(s, 0L);
        }
        for (Object[] row : requests.countByStatus()) {
            counts.put((RequestStatus) row[0], (Long) row[1]);
        }

        var from = time.startOf(monthStart);
        var to = time.startOf(monthStart.plusMonths(1));
        BigDecimal revenue = requests.revenue(RequestStatus.COMPLETED, from, to);
        long completed = requests.completedBetween(RequestStatus.COMPLETED, from, to);

        long accepted = 0;
        long rejected = 0;
        for (Object[] row : events.countReached(EnumSet.of(RequestStatus.ACCEPTED, RequestStatus.REJECTED),
                time.startOf(today.minusDays(90)))) {
            if (row[0] == RequestStatus.ACCEPTED) {
                accepted = (Long) row[1];
            } else {
                rejected = (Long) row[1];
            }
        }

        List<JobSlot> todayJobs = requests.onCalendar(
                        EnumSet.of(RequestStatus.SCHEDULED, RequestStatus.IN_PROGRESS, RequestStatus.COMPLETED),
                        time.startOf(today), time.startOf(today.plusDays(1)))
                .stream().map(JobSlot::from).toList();

        // Oldest waiting first: those customers have waited longest for a price.
        // الأقدم انتظارًا أول: هذولي العملاء انتظروا السعر أطول.
        List<RequestRow> waiting = requests.findByStatusOrderByCreatedAtAsc(RequestStatus.NEW,
                        PageRequest.of(0, LIST_SIZE))
                .stream().map(DashboardService::row).toList();
        List<RequestRow> expiring = requests.expiringQuotes(RequestStatus.QUOTED, today.plusDays(EXPIRING_DAYS),
                        PageRequest.of(0, LIST_SIZE))
                .stream().map(DashboardService::row).toList();

        Map<Long, ServiceType> byId = services.findAll().stream()
                .collect(Collectors.toMap(ServiceType::getId, Function.identity()));
        List<ServiceCount> top = requests.countByServiceSince(time.startOf(today.minusDays(30))).stream()
                .limit(LIST_SIZE)
                .map(row -> new ServiceCount(ServiceRef.of(byId.get((Long) row[0])), (Long) row[1]))
                .toList();

        return new DashboardResponse(counts, revenue, completed, acceptanceRate(accepted, rejected), todayJobs,
                waiting, expiring, top);
    }

    /**
     * Whole-percent acceptance rate, rounded half-up with integer maths; null when no quote was answered,
     * because "0%" would wrongly suggest every customer said no.
     * نسبة القبول بالمئة كرقم صحيح، مقرّبة نص فوق بحساب صحيح؛ null لو ما انرد على أي عرض،
     * لأن "0%" بتوحي غلط إن كل العملاء رفضوا.
     */
    public static Integer acceptanceRate(long accepted, long rejected) {
        long answered = accepted + rejected;
        if (answered == 0) {
            return null;
        }
        return (int) ((accepted * 200 + answered) / (answered * 2));
    }

    private static RequestRow row(ServiceRequest r) {
        var quote = r.getQuote();
        return new RequestRow(r.getId(), r.getReference(), r.getCustomerName(), ServiceRef.of(r.getServiceType()),
                r.getDistrict(), r.getStatus(), r.getCreatedAt(),
                quote == null ? null : quote.getValidUntil(), quote == null ? null : quote.getTotal());
    }
}
