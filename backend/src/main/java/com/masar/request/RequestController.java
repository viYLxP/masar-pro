package com.masar.request;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.masar.common.City;
import com.masar.common.PageResponse;
import com.masar.request.RequestDtos.BookingCreated;
import com.masar.request.RequestDtos.BookingRequest;
import com.masar.request.RequestDtos.CompleteRequest;
import com.masar.request.RequestDtos.QuoteRequest;
import com.masar.request.RequestDtos.ReasonRequest;
import com.masar.request.RequestDtos.RequestDetail;
import com.masar.request.RequestDtos.RequestSummary;
import com.masar.request.RequestDtos.ScheduleRequest;
import com.masar.request.RequestDtos.VersionRequest;

/**
 * Staff endpoints for requests. Office roles do everything; a technician may only open, start and complete
 * the jobs assigned to them (the service checks the assignment).
 * روابط الموظفين للطلبات. أدوار المكتب تسوي كل شي؛ والفني بس يفتح ويبدأ وينهي الأعمال المعيّنة له
 * (والخدمة تتأكد من التعيين).
 */
@RestController
@RequestMapping("/api/requests")
public class RequestController {

    private static final String OFFICE = "hasAnyRole('ADMIN', 'DISPATCHER')";

    private final RequestService requests;
    private final BookingService bookings;
    private final SchedulingService scheduling;

    public RequestController(RequestService requests, BookingService bookings, SchedulingService scheduling) {
        this.requests = requests;
        this.bookings = bookings;
        this.scheduling = scheduling;
    }

    // Oldest first by default: requests are handled in the order they arrived.
    // الأقدم أول افتراضيًا: الطلبات تنخدم بترتيب وصولها.
    @GetMapping
    @PreAuthorize(OFFICE)
    public PageResponse<RequestSummary> search(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) RequestStatus status,
                                               @RequestParam(required = false) City city,
                                               @RequestParam(required = false) Long serviceTypeId,
                                               @RequestParam(required = false) Long technicianId,
                                               @PageableDefault(size = 20, sort = {"createdAt", "id"},
                                                       direction = Sort.Direction.DESC) Pageable pageable) {
        return requests.search(q, status, city, serviceTypeId, technicianId, pageable);
    }

    // A booking typed in by the office (e.g. from a phone call). / حجز يدخله المكتب (مثلًا من مكالمة).
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(OFFICE)
    public BookingCreated create(@Valid @RequestBody BookingRequest body) {
        return bookings.create(body, true);
    }

    @GetMapping("/{id}")
    public RequestDetail get(@PathVariable Long id) {
        return requests.get(id);
    }

    @PutMapping("/{id}/quote")
    @PreAuthorize(OFFICE)
    public RequestDetail saveQuote(@PathVariable Long id, @Valid @RequestBody QuoteRequest body) {
        return requests.saveQuote(id, body);
    }

    @PostMapping("/{id}/quote/send")
    @PreAuthorize(OFFICE)
    public RequestDetail sendQuote(@PathVariable Long id, @Valid @RequestBody VersionRequest body) {
        return requests.sendQuote(id, body.version());
    }

    @PostMapping("/{id}/revise")
    @PreAuthorize(OFFICE)
    public RequestDetail revise(@PathVariable Long id, @Valid @RequestBody VersionRequest body) {
        return requests.revise(id, body.version());
    }

    @PostMapping("/{id}/schedule")
    @PreAuthorize(OFFICE)
    public RequestDetail schedule(@PathVariable Long id, @Valid @RequestBody ScheduleRequest body) {
        return scheduling.schedule(id, body);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(OFFICE)
    public RequestDetail cancel(@PathVariable Long id, @Valid @RequestBody ReasonRequest body) {
        return requests.cancel(id, body.reason(), body.version());
    }

    @PostMapping("/{id}/start")
    public RequestDetail start(@PathVariable Long id, @Valid @RequestBody VersionRequest body) {
        return scheduling.start(id, body.version());
    }

    @PostMapping("/{id}/complete")
    public RequestDetail complete(@PathVariable Long id, @Valid @RequestBody CompleteRequest body) {
        return scheduling.complete(id, body.note(), body.version());
    }
}
