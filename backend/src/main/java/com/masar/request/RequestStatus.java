package com.masar.request;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The life of a request, and which moves are allowed from each status.
 * مراحل الطلب، والانتقالات المسموحة من كل حالة.
 *
 * <p>Keeping the allowed moves in one table means every endpoint (staff or public) checks the same rules.</p>
 * <p>وجود الانتقالات المسموحة في جدول واحد يعني إن كل رابط (للموظف أو العام) يفحص نفس القواعد.</p>
 */
public enum RequestStatus {
    // Just received; the office prepares a quote. / وصل الحين؛ المكتب يجهّز عرض السعر.
    NEW,
    // Quote sent; waiting for the customer. / العرض انرسل؛ ننتظر العميل.
    QUOTED,
    // The customer accepted; waiting for a technician and a time. / العميل وافق؛ ننتظر فني ووقت.
    ACCEPTED,
    // A technician is booked. / الفني محجوز.
    SCHEDULED,
    // The technician is on site. / الفني في الموقع.
    IN_PROGRESS,
    COMPLETED,
    // The customer declined the quote. The office may revise it (back to NEW).
    // العميل رفض العرض. المكتب يقدر يعدّله (يرجع NEW).
    REJECTED,
    CANCELLED;

    private static final Map<RequestStatus, Set<RequestStatus>> NEXT = Map.of(
            NEW, EnumSet.of(QUOTED, CANCELLED),
            // QUOTED → NEW is "revise the quote" (withdraws the sent one). / QUOTED ← NEW يعني "تعديل العرض" (يسحب المرسل).
            QUOTED, EnumSet.of(ACCEPTED, REJECTED, NEW, CANCELLED),
            ACCEPTED, EnumSet.of(SCHEDULED, CANCELLED),
            // SCHEDULED → SCHEDULED is a reschedule. / SCHEDULED ← SCHEDULED يعني إعادة جدولة.
            SCHEDULED, EnumSet.of(SCHEDULED, IN_PROGRESS, CANCELLED),
            IN_PROGRESS, EnumSet.of(COMPLETED),
            COMPLETED, EnumSet.noneOf(RequestStatus.class),
            REJECTED, EnumSet.of(NEW),
            CANCELLED, EnumSet.noneOf(RequestStatus.class));

    // Statuses where a technician's time is taken (used by the overlap check).
    // الحالات اللي يكون فيها وقت الفني محجوز (يستخدمها فحص التعارض).
    public static final Set<RequestStatus> BOOKED = EnumSet.of(SCHEDULED, IN_PROGRESS);

    public boolean canMoveTo(RequestStatus target) {
        return NEXT.get(this).contains(target);
    }

    public boolean isFinal() {
        return NEXT.get(this).isEmpty();
    }
}
