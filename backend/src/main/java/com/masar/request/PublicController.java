package com.masar.request;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.masar.catalog.ServiceTypeDtos.ServiceTypeResponse;
import com.masar.catalog.ServiceTypeService;
import com.masar.request.RequestDtos.BookingCreated;
import com.masar.request.RequestDtos.BookingRequest;
import com.masar.request.RequestDtos.CustomerResponse;
import com.masar.request.RequestDtos.PublicView;
import com.masar.request.RequestDtos.TrackRequest;

/**
 * Endpoints for customers, without login. Rate-limited per IP (see PublicRateLimitFilter).
 * روابط العملاء بدون تسجيل دخول. محدودة لكل عنوان IP (شوف PublicRateLimitFilter).
 *
 * <p>Tracking uses POST with the phone in the body, not GET with it in the URL, so phone numbers never end up in
 * server or proxy logs.</p>
 * <p>التتبع يستخدم POST والجوال داخل الطلب، مو GET والجوال في الرابط، حتى أرقام الجوالات ما توصل لسجلات
 * الخادم أو البروكسي.</p>
 */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final ServiceTypeService services;
    private final BookingService bookings;

    public PublicController(ServiceTypeService services, BookingService bookings) {
        this.services = services;
        this.bookings = bookings;
    }

    @GetMapping("/services")
    public List<ServiceTypeResponse> services() {
        return services.listActive();
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingCreated book(@Valid @RequestBody BookingRequest body) {
        return bookings.create(body, false);
    }

    @PostMapping("/requests/track")
    public PublicView track(@Valid @RequestBody TrackRequest body) {
        return bookings.track(body.reference(), body.phone());
    }

    @PostMapping("/requests/{reference}/accept")
    public PublicView accept(@PathVariable String reference, @Valid @RequestBody CustomerResponse body) {
        return bookings.accept(reference, body.phone());
    }

    @PostMapping("/requests/{reference}/reject")
    public PublicView reject(@PathVariable String reference, @Valid @RequestBody CustomerResponse body) {
        return bookings.reject(reference, body.phone(), body.reason());
    }
}
