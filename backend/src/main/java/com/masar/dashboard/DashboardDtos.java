package com.masar.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.masar.catalog.ServiceTypeDtos.ServiceRef;
import com.masar.request.RequestDtos.JobSlot;
import com.masar.request.RequestStatus;

/**
 * The office dashboard in one response, so the page loads with a single call.
 * لوحة تحكم المكتب في رد واحد، حتى الصفحة تحمّل بطلب واحد.
 */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    // A short row for "waiting for a quote" and "quotes about to expire". / صف مختصر للانتظار والعروض اللي بتنتهي.
    public record RequestRow(Long id, String reference, String customerName, ServiceRef service, String district,
                             RequestStatus status, Instant createdAt, LocalDate validUntil, BigDecimal total) {
    }

    public record ServiceCount(ServiceRef service, long count) {
    }

    /**
     * @param statusCounts     how many requests are in each status right now / عدد الطلبات في كل حالة الحين
     * @param revenueThisMonth sum of completed jobs' quote totals this month (incl. VAT) / مجموع أعمال الشهر المكتملة (شامل الضريبة)
     * @param completedThisMonth number of jobs completed this month / عدد الأعمال المكتملة هذا الشهر
     * @param acceptanceRate   % of answered quotes that were accepted in the last 90 days; null when none were answered
     *                         / نسبة العروض المقبولة من اللي انرد عليها آخر 90 يوم؛ null لو ما انرد على أي عرض
     */
    public record DashboardResponse(Map<RequestStatus, Long> statusCounts, BigDecimal revenueThisMonth,
                                    long completedThisMonth, Integer acceptanceRate, List<JobSlot> today,
                                    List<RequestRow> waitingForQuote, List<RequestRow> expiringQuotes,
                                    List<ServiceCount> topServices) {
    }
}
