package com.masar.request;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masar.catalog.ServiceType;
import com.masar.catalog.ServiceTypeService;
import com.masar.common.ApiException;
import com.masar.common.BusinessTime;
import com.masar.common.ErrorCode;
import com.masar.common.Phones;
import com.masar.common.Text;
import com.masar.payment.Payment;
import com.masar.payment.PaymentDtos.CheckoutResponse;
import com.masar.payment.PaymentService;
import com.masar.request.RequestDtos.BookingCreated;
import com.masar.request.RequestDtos.BookingRequest;
import com.masar.request.RequestDtos.PublicView;
import com.masar.user.AppUser;

/**
 * The customer side: booking a visit, tracking it, and answering the quote.
 * جهة العميل: حجز زيارة، وتتبعها، والرد على عرض السعر.
 */
@Service
public class BookingService {

    // Customers can ask for a visit up to two months ahead. / العميل يقدر يطلب زيارة لين شهرين قدّام.
    static final int MAX_DAYS_AHEAD = 60;

    private final ServiceRequestRepository requests;
    private final RequestEventRepository events;
    private final ServiceTypeService services;
    private final ReferenceGenerator references;
    private final RequestHistory history;
    private final BusinessTime time;
    private final PaymentService payments;

    public BookingService(ServiceRequestRepository requests, RequestEventRepository events,
                          ServiceTypeService services, ReferenceGenerator references, RequestHistory history,
                          BusinessTime time, PaymentService payments) {
        this.requests = requests;
        this.events = events;
        this.services = services;
        this.references = references;
        this.history = history;
        this.time = time;
        this.payments = payments;
    }

    /**
     * Creates a request. fromOffice = true when a dispatcher types it in during a phone call.
     * ينشئ طلب. fromOffice = true لما الموزّع يدخله أثناء مكالمة.
     */
    @Transactional
    public BookingCreated create(BookingRequest request, boolean fromOffice) {
        // A filled honeypot means a bot. We answer like a success so it does not learn to skip the field,
        // but nothing is saved.
        // المصيدة المعبّاة تعني بوت. نرد كأنه نجح حتى ما يتعلم يتجاوز الحقل، لكن ما ينحفظ شي.
        if (!fromOffice && Text.blankToNull(request.website()) != null) {
            return new BookingCreated(references.next(), RequestStatus.NEW);
        }
        return save(request, Phones.require(request.phone(), "phone"), null, fromOffice);
    }

    /**
     * A booking by a signed-in customer. The phone is the account's own, whatever the form sent, so a customer
     * cannot attach a request to someone else's number.
     * حجز من عميل مسجّل دخول. الجوال هو جوال الحساب مهما أرسل النموذج، حتى العميل ما يقدر يربط طلب برقم غيره.
     */
    @Transactional
    public BookingCreated createForCustomer(BookingRequest request, AppUser customer) {
        return save(request, customer.getPhone(), customer, false);
    }

    private BookingCreated save(BookingRequest request, String phone, AppUser customer, boolean fromOffice) {
        ServiceType service = services.find(request.serviceTypeId());
        if (!service.isActive()) {
            throw new ApiException(ErrorCode.SERVICE_INACTIVE);
        }
        checkPreferredTime(request.preferredDate(), request.preferredSlot());

        ServiceRequest entity = new ServiceRequest(uniqueReference(), service);
        entity.setCustomerName(request.customerName().trim());
        entity.setPhone(phone);
        entity.setEmail(Text.blankToNull(request.email()));
        entity.setCity(request.city());
        entity.setDistrict(request.district().trim());
        entity.setAddress(Text.blankToNull(request.address()));
        entity.setPropertyType(request.propertyType());
        entity.setPreferredDate(request.preferredDate());
        entity.setPreferredSlot(request.preferredSlot());
        entity.setDetails(Text.blankToNull(request.details()));
        entity.setCustomer(customer);
        requests.save(entity);
        if (fromOffice) {
            history.staff(entity, null, null, time.now());
        } else {
            history.customer(entity, null, null, time.now());
        }
        return new BookingCreated(entity.getReference(), entity.getStatus());
    }

    @Transactional(readOnly = true)
    public PublicView track(String reference, String phone) {
        return view(findForCustomer(reference, phone));
    }

    @Transactional
    public PublicView accept(String reference, String phone) {
        return accept(findForCustomer(reference, phone));
    }

    @Transactional
    public PublicView reject(String reference, String phone, String reason) {
        return reject(findForCustomer(reference, phone), reason);
    }

    /**
     * The customer accepts the sent quote. Refused after its "valid until" date, because prices may have changed.
     * العميل يقبل العرض المرسل. مرفوض بعد تاريخ الصلاحية، لأن الأسعار ممكن تغيّرت.
     */
    @Transactional
    public PublicView accept(ServiceRequest request) {
        request.requireStatus(RequestStatus.QUOTED);
        if (request.getQuote().isExpired(time.today())) {
            throw new ApiException(ErrorCode.QUOTE_EXPIRED);
        }
        RequestStatus from = request.moveTo(RequestStatus.ACCEPTED);
        request.getQuote().setRespondedAt(time.now());
        history.customer(request, from, null, time.now());
        return view(request);
    }

    /**
     * The customer declines the quote. A reason is required: it is what the office needs to revise the price.
     * العميل يرفض العرض. السبب إلزامي: هو اللي يحتاجه المكتب عشان يعدّل السعر.
     */
    @Transactional
    public PublicView reject(ServiceRequest request, String reason) {
        String cleanReason = Text.blankToNull(reason);
        if (cleanReason == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("reason", "NotBlank")));
        }
        RequestStatus from = request.moveTo(RequestStatus.REJECTED);
        request.setRejectReason(cleanReason);
        request.getQuote().setRespondedAt(time.now());
        history.customer(request, from, cleanReason, time.now());
        return view(request);
    }

    /**
     * Opens online payment for a guest, who proves the request is theirs with the phone, like on the tracking page.
     * يفتح الدفع أونلاين للزائر، واللي يثبت إن الطلب له بالجوال، مثل صفحة التتبع.
     */
    @Transactional
    public CheckoutResponse pay(String reference, String phone, Payment.Method method) {
        return payments.startOnline(findForCustomer(reference, phone), method);
    }

    // The customer's view, with the payment part. / عرض العميل، مع جزء الدفع.
    public PublicView view(ServiceRequest request) {
        return PublicView.from(request, events.findByRequestOrderByCreatedAtAscIdAsc(request), time.today(),
                payments.summary(request));
    }

    /**
     * Finds a request by reference AND phone. A wrong phone gives the same NOT_FOUND as a wrong reference,
     * so the answer never reveals that a reference exists.
     * يلقى الطلب بالرقم المرجعي والجوال مع بعض. الجوال الغلط يرجّع نفس NOT_FOUND مثل الرقم المرجعي الغلط،
     * فالرد ما يكشف أبدًا إن الرقم المرجعي موجود.
     */
    private ServiceRequest findForCustomer(String reference, String phone) {
        String normalized = Phones.normalize(phone);
        return requests.findByReference(ReferenceGenerator.clean(reference))
                .filter(r -> r.getPhone().equals(normalized))
                .orElseThrow(() -> ApiException.notFound("request", ReferenceGenerator.clean(reference)));
    }

    /**
     * The preferred visit must be today or later (and, today, in a slot that has not ended yet), at most 60 days ahead.
     * الزيارة المطلوبة لازم تكون اليوم أو بعده (واليوم في فترة ما خلصت)، وبحد أقصى 60 يوم قدّام.
     */
    private void checkPreferredTime(LocalDate date, TimeSlot slot) {
        LocalDate today = time.today();
        if (date.isBefore(today)) {
            throw fieldError("preferredDate", "FutureOrPresent");
        }
        if (date.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw fieldError("preferredDate", "Max");
        }
        if (date.equals(today) && !time.timeOf(time.now()).isBefore(slot.end())) {
            throw fieldError("preferredSlot", "FutureOrPresent");
        }
    }

    // Retry on the (very unlikely) clash; the UNIQUE index is the final guard.
    // نعيد المحاولة لو صار تكرار (احتمال ضعيف جدًا)، والفهرس الفريد هو الحارس الأخير.
    private String uniqueReference() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = references.next();
            if (!requests.existsByReference(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique reference");
    }

    private static ApiException fieldError(String field, String rule) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of(field, rule)));
    }
}
