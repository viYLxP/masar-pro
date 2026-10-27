package com.masar.request;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.JoinType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masar.common.ApiException;
import com.masar.common.BusinessTime;
import com.masar.common.City;
import com.masar.common.ErrorCode;
import com.masar.common.PageResponse;
import com.masar.common.Phones;
import com.masar.common.Text;
import com.masar.common.Versions;
import com.masar.payment.PaymentService;
import com.masar.request.RequestDtos.QuoteRequest;
import com.masar.request.RequestDtos.RequestDetail;
import com.masar.request.RequestDtos.RequestSummary;
import com.masar.user.AppUser;
import com.masar.user.CurrentUserService;

/**
 * The office side of a request: finding it, writing and sending the quote, revising and cancelling.
 * جهة المكتب للطلب: البحث، كتابة العرض وإرساله، التعديل والإلغاء.
 */
@Service
public class RequestService {

    // A quote can be valid for at most two months. / العرض يكون صالح لشهرين بالكثير.
    static final int MAX_QUOTE_DAYS = 60;

    private final ServiceRequestRepository requests;
    private final RequestEventRepository events;
    private final RequestHistory history;
    private final CurrentUserService currentUser;
    private final BusinessTime time;
    private final EntityManager entityManager;
    private final PaymentService payments;

    public RequestService(ServiceRequestRepository requests, RequestEventRepository events, RequestHistory history,
                          CurrentUserService currentUser, BusinessTime time, EntityManager entityManager,
                          PaymentService payments) {
        this.payments = payments;
        this.entityManager = entityManager;
        this.requests = requests;
        this.events = events;
        this.history = history;
        this.currentUser = currentUser;
        this.time = time;
    }

    /**
     * Office list with filters. q matches the reference, customer name, phone or district.
     * قائمة المكتب مع فلاتر. q يطابق الرقم المرجعي أو اسم العميل أو الجوال أو الحي.
     */
    @Transactional(readOnly = true)
    public PageResponse<RequestSummary> search(String q, RequestStatus status, City city, Long serviceTypeId,
                                               Long technicianId, Pageable pageable) {
        List<Specification<ServiceRequest>> filters = new ArrayList<>();
        String like = Text.likePattern(q);
        if (like != null) {
            // Typing "0551234567" should find "966551234567", so the phone is also searched in its stored form.
            // كتابة "0551234567" لازم تلقى "966551234567"، فنبحث بالجوال كمان بشكله المحفوظ.
            String phone = Phones.normalize(q);
            filters.add((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("reference")), like, '\\'),
                    cb.like(cb.lower(root.get("customerName")), like, '\\'),
                    cb.like(cb.lower(root.get("district")), like, '\\'),
                    cb.like(root.get("phone"), phone != null ? phone : like, '\\')));
        }
        if (status != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (city != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("city"), city));
        }
        if (serviceTypeId != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("serviceType").get("id"), serviceTypeId));
        }
        if (technicianId != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("technician").get("id"), technicianId));
        }
        // Fetch the service, technician and quote with the rows, but not in the count query (it has no rows to join).
        // نجيب الخدمة والفني والعرض مع الصفوف، لكن مو في استعلام العدّ (ما فيه صفوف نربطها).
        filters.add((root, query, cb) -> {
            if (query != null && !Long.class.equals(query.getResultType())) {
                root.fetch("serviceType", JoinType.LEFT);
                root.fetch("technician", JoinType.LEFT);
                root.fetch("quote", JoinType.LEFT);
            }
            return cb.conjunction();
        });
        // Spring Data 4's allOf rejects nulls, so only real filters are in the list.
        // allOf في Spring Data 4 يرفض null، فالقائمة فيها بس الفلاتر الفعلية.
        return PageResponse.of(requests.findAll(Specification.allOf(filters), pageable), RequestSummary::from);
    }

    /**
     * One request. A technician may only open the jobs assigned to them.
     * طلب واحد. الفني يقدر يفتح بس الأعمال المعيّنة له.
     */
    @Transactional(readOnly = true)
    public RequestDetail get(Long id) {
        ServiceRequest request = find(id);
        requireAccess(request);
        return detail(request);
    }

    /**
     * Writes (or rewrites) the quote. Only while the request is NEW: once sent, it must be withdrawn first,
     * so the customer never accepts a price that changed after they saw it.
     * يكتب العرض (أو يعيد كتابته). فقط والطلب NEW: بعد الإرسال لازم يتسحب أول، حتى العميل ما يقبل سعر
     * تغيّر بعد ما شافه.
     */
    @Transactional
    public RequestDetail saveQuote(Long id, QuoteRequest body) {
        ServiceRequest request = find(id);
        Versions.check(request.getVersion(), body.version());
        request.requireStatus(RequestStatus.NEW);
        LocalDate today = time.today();
        if (body.validUntil().isBefore(today)) {
            throw fieldError("validUntil", "FutureOrPresent");
        }
        if (body.validUntil().isAfter(today.plusDays(MAX_QUOTE_DAYS))) {
            throw fieldError("validUntil", "Max");
        }
        Quote quote = request.getQuote();
        if (quote == null) {
            quote = new Quote(request);
            request.setQuote(quote);
        } else {
            quote.clearLines();
            // Delete the old lines now, before the new ones are inserted (see Quote.clearLines).
            // نحذف البنود القديمة الحين، قبل ما تنضاف الجديدة (شوف Quote.clearLines).
            requests.flush();
        }
        quote.setValidUntil(body.validUntil());
        quote.setNotes(Text.blankToNull(body.notes()));
        quote.addLines(body.lines().stream()
                .map(l -> new QuoteLine(l.description().trim(), l.quantity(), l.unitPrice()))
                .toList());
        // Only quote rows changed, so the request row is not dirty and its @Version would stay the same. Force the
        // increment now (PESSIMISTIC_ runs the UPDATE at once, so the response already carries the new version):
        // another dispatcher's open copy of this request is out of date and must reload.
        // اللي تغيّر صفوف العرض بس، فصف الطلب ما تعدّل ورقم @Version بيبقى نفسه. نجبر الزيادة الحين
        // (PESSIMISTIC_ ينفّذ التحديث فورًا، فالرد يحمل رقم النسخة الجديد): النسخة المفتوحة عند موزّع ثاني صارت قديمة.
        entityManager.lock(request, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        requests.flush();
        return detail(request);
    }

    /**
     * Sends the quote to the customer: NEW → QUOTED. The tracking page shows it from now on.
     * يرسل العرض للعميل: NEW ← QUOTED. صفحة التتبع تعرضه من الحين.
     */
    @Transactional
    public RequestDetail sendQuote(Long id, Long version) {
        ServiceRequest request = find(id);
        Versions.check(request.getVersion(), version);
        request.requireStatus(RequestStatus.NEW);
        Quote quote = request.getQuote();
        if (quote == null || quote.getLines().isEmpty()) {
            throw new ApiException(ErrorCode.QUOTE_REQUIRED);
        }
        // An out-of-date draft would arrive already expired; refuse so the dispatcher fixes the date.
        // المسودة القديمة بتوصل منتهية؛ نرفض حتى يعدّل الموزّع التاريخ.
        if (quote.isExpired(time.today())) {
            throw new ApiException(ErrorCode.QUOTE_EXPIRED);
        }
        RequestStatus from = request.moveTo(RequestStatus.QUOTED);
        quote.setSentAt(time.now());
        quote.setRespondedAt(null);
        history.staff(request, from, null, time.now());
        return detail(request);
    }

    /**
     * Takes the request back to NEW to change the quote: withdraws a sent quote, or revises a rejected one.
     * يرجّع الطلب NEW عشان يتعدل العرض: يسحب عرض مرسل، أو يعدّل عرض مرفوض.
     */
    @Transactional
    public RequestDetail revise(Long id, Long version) {
        ServiceRequest request = find(id);
        Versions.check(request.getVersion(), version);
        RequestStatus from = request.moveTo(RequestStatus.NEW);
        request.setRejectReason(null);
        request.getQuote().setSentAt(null);
        request.getQuote().setRespondedAt(null);
        history.staff(request, from, null, time.now());
        return detail(request);
    }

    @Transactional
    public RequestDetail cancel(Long id, String reason, Long version) {
        ServiceRequest request = find(id);
        Versions.check(request.getVersion(), version);
        RequestStatus from = request.moveTo(RequestStatus.CANCELLED);
        request.setCancelReason(reason.trim());
        // A paid request gets its money back; this runs before the commit, so a failed refund keeps it open.
        // الطلب المدفوع يرجع مبلغه؛ وهذا يصير قبل الحفظ، فلو فشل الاسترجاع يبقى الطلب مفتوح.
        payments.refundIfPaid(request);
        history.staff(request, from, reason, time.now());
        return detail(request);
    }

    /**
     * Records a payment received outside the site (cash to the technician, or a transfer the office confirmed).
     * يسجّل دفعة وصلت برا الموقع (كاش للفني، أو تحويل تأكد منه المكتب).
     */
    @Transactional
    public RequestDetail recordCash(Long id, Long version) {
        ServiceRequest request = find(id);
        requireAccess(request);
        Versions.check(request.getVersion(), version);
        payments.recordCash(request);
        return detail(request);
    }

    ServiceRequest find(Long id) {
        return requests.findById(id).orElseThrow(() -> ApiException.notFound("request", id));
    }

    /**
     * Office staff see everything; a technician only what is assigned to them.
     * موظفين المكتب يشوفون كل شي؛ والفني بس المعيّن له.
     */
    AppUser requireAccess(ServiceRequest request) {
        AppUser me = currentUser.require();
        if (!CurrentUserService.isOffice(me)
                && (request.getTechnician() == null || !Objects.equals(request.getTechnician().getId(), me.getId()))) {
            throw new ApiException(ErrorCode.NOT_ASSIGNED);
        }
        return me;
    }

    /**
     * Builds the response after a change. It flushes first: the version column is only bumped when Hibernate
     * writes the row, and the client must receive the new version or its next action fails as a conflict.
     * يبني الرد بعد أي تعديل. نسوي flush أول: عمود الإصدار ما يزيد إلا لما Hibernate يكتب الصف،
     * والواجهة لازم تستلم الإصدار الجديد وإلا إجراءها الجاي يفشل كتعارض.
     */
    RequestDetail detail(ServiceRequest request) {
        entityManager.flush();
        return RequestDetail.from(request, events.findByRequestOrderByCreatedAtAscIdAsc(request), time.today(),
                payments.history(request));
    }

    private static ApiException fieldError(String field, String rule) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of(field, rule)));
    }
}
