package com.masar.request;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.masar.catalog.ServiceTypeDtos.ServiceRef;
import com.masar.common.City;
import com.masar.payment.PaymentDtos.PaymentSummary;
import com.masar.payment.PaymentDtos.PaymentView;
import com.masar.user.UserRef;

/**
 * Request/response shapes for bookings, quotes and jobs.
 * أشكال الطلب والرد للحجوزات وعروض الأسعار والأعمال.
 */
public final class RequestDtos {

    private RequestDtos() {
    }

    // ----- Booking (public site, or the office taking a phone call) / الحجز (الموقع العام، أو المكتب من مكالمة) -----

    public record BookingRequest(
            @NotBlank @Size(max = 120) String customerName,
            @NotBlank @Size(max = 20) String phone,
            @Email @Size(max = 150) String email,
            @NotNull City city,
            @NotBlank @Size(max = 100) String district,
            @Size(max = 300) String address,
            @NotNull PropertyType propertyType,
            @NotNull Long serviceTypeId,
            @NotNull LocalDate preferredDate,
            @NotNull TimeSlot preferredSlot,
            @Size(max = 2000) String details,
            // Honeypot: a field hidden from people. Bots fill every field, so a value here marks the request as spam.
            // مصيدة: حقل مخفي عن الناس. البوتات تعبّي كل الحقول، فأي قيمة هنا تعني إن الطلب مزعج.
            @Size(max = 200) String website) {
    }

    public record BookingCreated(String reference, RequestStatus status) {
    }

    // ----- Customer actions on the public site / عمليات العميل في الموقع العام -----

    // Looking up a request needs both the reference and the phone, so a leaked reference alone shows nothing.
    // البحث عن طلب يحتاج الرقم المرجعي والجوال مع بعض، فلو تسرّب الرقم المرجعي لحاله ما يبين شي.
    public record TrackRequest(@NotBlank @Size(max = 20) String reference, @NotBlank @Size(max = 20) String phone) {
    }

    public record CustomerResponse(@NotBlank @Size(max = 20) String phone, @Size(max = 255) String reason) {
    }

    // ----- Office actions / عمليات المكتب -----

    public record QuoteLineRequest(
            @NotBlank @Size(max = 200) String description,
            @NotNull @Min(1) @Max(999) Integer quantity,
            @NotNull @DecimalMin("0") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2) BigDecimal unitPrice) {
    }

    public record QuoteRequest(
            @NotEmpty @Size(max = 20) List<@Valid @NotNull QuoteLineRequest> lines,
            @NotNull LocalDate validUntil,
            @Size(max = 1000) String notes,
            @NotNull Long version) {
    }

    public record ScheduleRequest(
            @NotNull Long technicianId,
            @NotNull LocalDate date,
            // Local time in Riyadh, on the half hour (e.g. 09:00, 13:30). / الوقت بتوقيت الرياض على نص الساعة.
            @NotNull LocalTime startTime,
            @NotNull Long version) {
    }

    public record ReasonRequest(@NotBlank @Size(max = 255) String reason, @NotNull Long version) {
    }

    // cashCollected: the technician took the payment in cash at the end of the visit.
    // cashCollected: الفني استلم المبلغ كاش في نهاية الزيارة.
    public record CompleteRequest(@Size(max = 1000) String note, boolean cashCollected, @NotNull Long version) {
    }

    public record VersionRequest(@NotNull Long version) {
    }

    // ----- Responses / الردود -----

    public record QuoteLineView(String description, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {

        static QuoteLineView of(QuoteLine l) {
            return new QuoteLineView(l.getDescription(), l.getQuantity(), l.getUnitPrice(), l.getLineTotal());
        }
    }

    public record QuoteView(LocalDate validUntil, boolean expired, BigDecimal subtotal, BigDecimal vat,
                            BigDecimal total, String notes, Instant sentAt, Instant respondedAt,
                            List<QuoteLineView> lines) {

        static QuoteView of(Quote q, LocalDate today) {
            if (q == null) {
                return null;
            }
            return new QuoteView(q.getValidUntil(), q.isExpired(today), q.getSubtotal(), q.getVat(), q.getTotal(),
                    q.getNotes(), q.getSentAt(), q.getRespondedAt(),
                    q.getLines().stream().map(QuoteLineView::of).toList());
        }
    }

    public record EventView(RequestStatus fromStatus, RequestStatus toStatus, String actor, String note,
                            Instant createdAt) {

        static EventView of(RequestEvent e) {
            return new EventView(e.getFromStatus(), e.getToStatus(), e.getActor(), e.getNote(), e.getCreatedAt());
        }
    }

    // A row in the office list. / صف في قائمة المكتب.
    public record RequestSummary(Long id, String reference, String customerName, String phone, City city,
                                 String district, ServiceRef service, LocalDate preferredDate,
                                 TimeSlot preferredSlot, RequestStatus status, UserRef technician,
                                 Instant scheduledStart, Instant scheduledEnd, BigDecimal quoteTotal,
                                 PaymentStatus paymentStatus, Instant createdAt) {

        public static RequestSummary from(ServiceRequest r) {
            return new RequestSummary(r.getId(), r.getReference(), r.getCustomerName(), r.getPhone(), r.getCity(),
                    r.getDistrict(), ServiceRef.of(r.getServiceType()), r.getPreferredDate(), r.getPreferredSlot(),
                    r.getStatus(), UserRef.of(r.getTechnician()), r.getScheduledStart(), r.getScheduledEnd(),
                    r.getQuote() == null ? null : r.getQuote().getTotal(), r.getPaymentStatus(), r.getCreatedAt());
        }
    }

    // Everything the office (or the assigned technician) sees on one request. / كل اللي يشوفه المكتب (أو الفني المعيّن).
    public record RequestDetail(Long id, String reference, RequestStatus status, String customerName, String phone,
                                String email, City city, String district, String address,
                                PropertyType propertyType, ServiceRef service, int durationMinutes,
                                LocalDate preferredDate, TimeSlot preferredSlot, String details,
                                UserRef technician, Instant scheduledStart, Instant scheduledEnd,
                                Instant startedAt, Instant completedAt, String completionNote,
                                String rejectReason, String cancelReason, QuoteView quote,
                                List<EventView> history, PaymentStatus paymentStatus, List<PaymentView> payments,
                                boolean registeredCustomer, Instant createdAt, Long version) {

        public static RequestDetail from(ServiceRequest r, List<RequestEvent> events, LocalDate today,
                                         List<PaymentView> payments) {
            return new RequestDetail(r.getId(), r.getReference(), r.getStatus(), r.getCustomerName(), r.getPhone(),
                    r.getEmail(), r.getCity(), r.getDistrict(), r.getAddress(), r.getPropertyType(),
                    ServiceRef.of(r.getServiceType()), r.getServiceType().getDurationMinutes(),
                    r.getPreferredDate(), r.getPreferredSlot(), r.getDetails(), UserRef.of(r.getTechnician()),
                    r.getScheduledStart(), r.getScheduledEnd(), r.getStartedAt(), r.getCompletedAt(),
                    r.getCompletionNote(), r.getRejectReason(), r.getCancelReason(),
                    QuoteView.of(r.getQuote(), today), events.stream().map(EventView::of).toList(),
                    r.getPaymentStatus(), payments, r.getCustomer() != null, r.getCreatedAt(), r.getVersion());
        }
    }

    /**
     * What the customer sees on the tracking page: no staff usernames, no internal notes, only the technician's name.
     * اللي يشوفه العميل في صفحة التتبع: بدون أسماء دخول الموظفين ولا ملاحظات داخلية، بس اسم الفني.
     */
    public record PublicView(String reference, RequestStatus status, String customerName, ServiceRef service,
                             City city, String district, LocalDate preferredDate, TimeSlot preferredSlot,
                             QuoteView quote, String technicianName, Instant scheduledStart, Instant scheduledEnd,
                             Instant completedAt, String rejectReason, String cancelReason,
                             List<PublicEvent> history, PaymentSummary payment, Instant createdAt) {

        public static PublicView from(ServiceRequest r, List<RequestEvent> events, LocalDate today,
                                      PaymentSummary payment) {
            // A quote is shown only after it was sent: a draft the office is still writing stays private.
            // العرض يظهر بس بعد ما ينرسل: المسودة اللي المكتب لسا يكتبها تبقى خاصة.
            Quote quote = r.getQuote() != null && r.getQuote().getSentAt() != null ? r.getQuote() : null;
            return new PublicView(r.getReference(), r.getStatus(), r.getCustomerName(),
                    ServiceRef.of(r.getServiceType()), r.getCity(), r.getDistrict(), r.getPreferredDate(),
                    r.getPreferredSlot(), QuoteView.of(quote, today),
                    r.getTechnician() == null ? null : r.getTechnician().getFullName(),
                    r.getScheduledStart(), r.getScheduledEnd(), r.getCompletedAt(), r.getRejectReason(),
                    r.getCancelReason(),
                    events.stream().map(e -> new PublicEvent(e.getToStatus(), e.getCreatedAt())).toList(),
                    payment, r.getCreatedAt());
        }
    }

    public record PublicEvent(RequestStatus status, Instant at) {
    }

    // A row in the customer's "My requests" list. / صف في قائمة "طلباتي" عند العميل.
    public record AccountRequestRow(String reference, RequestStatus status, ServiceRef service, City city,
                                    String district, LocalDate preferredDate, TimeSlot preferredSlot,
                                    Instant scheduledStart, BigDecimal quoteTotal, PaymentStatus paymentStatus,
                                    boolean payable, Instant createdAt) {
    }

    // Adds a request booked as a guest to the account. / يضيف طلب انحجز كزائر للحساب.
    public record ClaimRequest(@NotBlank @Size(max = 20) String reference) {
    }

    public record RejectRequest(@NotBlank @Size(max = 255) String reason) {
    }

    // ----- Calendar / الجدول -----

    public record JobSlot(Long id, String reference, RequestStatus status, ServiceRef service, String customerName,
                          String district, Instant start, Instant end) {

        public static JobSlot from(ServiceRequest r) {
            return new JobSlot(r.getId(), r.getReference(), r.getStatus(), ServiceRef.of(r.getServiceType()),
                    r.getCustomerName(), r.getDistrict(), r.getScheduledStart(), r.getScheduledEnd());
        }
    }

    public record TechnicianDay(Long id, String fullName, City city, List<ServiceRef> skills, List<JobSlot> jobs) {
    }

    public record DayBoard(LocalDate date, List<TechnicianDay> technicians) {
    }

    // A job card in the technician's "My jobs" list. / بطاقة شغل في قائمة "أعمالي" للفني.
    public record JobCard(Long id, String reference, RequestStatus status, ServiceRef service, String customerName,
                          String phone, City city, String district, String address, String details,
                          Instant scheduledStart, Instant scheduledEnd, Instant completedAt, Long version) {

        public static JobCard from(ServiceRequest r) {
            return new JobCard(r.getId(), r.getReference(), r.getStatus(), ServiceRef.of(r.getServiceType()),
                    r.getCustomerName(), r.getPhone(), r.getCity(), r.getDistrict(), r.getAddress(), r.getDetails(),
                    r.getScheduledStart(), r.getScheduledEnd(), r.getCompletedAt(), r.getVersion());
        }
    }
}
