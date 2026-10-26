package com.masar.request;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.masar.catalog.ServiceType;
import com.masar.common.ApiException;
import com.masar.common.City;
import com.masar.common.ErrorCode;
import com.masar.user.AppUser;

/**
 * A customer's request for a service, from booking to completed job.
 * طلب خدمة من عميل، من الحجز لين ينتهي الشغل.
 *
 * <p>The status only changes through {@link #moveTo}, which refuses moves that {@link RequestStatus} does not allow.
 * The database CHECK constraints repeat the most important rules.</p>
 * <p>الحالة تتغير فقط عبر moveTo، اللي يرفض أي انتقال ما يسمح فيه RequestStatus.
 * وقيود CHECK في قاعدة البيانات تكرر أهم القواعد.</p>
 */
@Entity
@Table(name = "service_request")
public class ServiceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 12)
    private String reference;

    @Column(name = "customer_name", nullable = false, length = 120)
    private String customerName;

    @Column(nullable = false, length = 12)
    private String phone;

    @Column(length = 150)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private City city;

    @Column(nullable = false, length = 100)
    private String district;

    @Column(length = 300)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", nullable = false, length = 20)
    private PropertyType propertyType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_type_id")
    private ServiceType serviceType;

    @Column(name = "preferred_date", nullable = false)
    private LocalDate preferredDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_slot", nullable = false, length = 20)
    private TimeSlot preferredSlot;

    @Column(length = 2000)
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status = RequestStatus.NEW;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "technician_id")
    private AppUser technician;

    @Column(name = "scheduled_start")
    private Instant scheduledStart;

    @Column(name = "scheduled_end")
    private Instant scheduledEnd;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completion_note", length = 1000)
    private String completionNote;

    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    // The quote is owned by the request: saving or deleting the request does the same to its quote.
    // عرض السعر تابع للطلب: حفظ أو حذف الطلب يسوي نفس الشي للعرض.
    @OneToOne(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Quote quote;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected ServiceRequest() {
    }

    public ServiceRequest(String reference, ServiceType serviceType) {
        this.reference = reference;
        this.serviceType = serviceType;
    }

    /**
     * Moves to a new status and returns the previous one, or fails with INVALID_STATUS (the current status is in
     * the details so the UI can say "not allowed while the request is Completed").
     * ينقل لحالة جديدة ويرجّع السابقة، أو يرمي INVALID_STATUS (والحالة الحالية في التفاصيل حتى تقول الواجهة
     * "غير مسموح والطلب مكتمل").
     */
    public RequestStatus moveTo(RequestStatus target) {
        if (!status.canMoveTo(target)) {
            throw new ApiException(ErrorCode.INVALID_STATUS, Map.of("status", status.name()));
        }
        RequestStatus previous = status;
        status = target;
        return previous;
    }

    /**
     * Fails unless the request is in one of the given statuses (for actions that do not change the status).
     * يرمي خطأ إلا لو الطلب في وحدة من الحالات المعطاة (للعمليات اللي ما تغيّر الحالة).
     */
    public void requireStatus(RequestStatus... allowed) {
        for (RequestStatus s : allowed) {
            if (status == s) {
                return;
            }
        }
        throw new ApiException(ErrorCode.INVALID_STATUS, Map.of("status", status.name()));
    }

    public void book(AppUser technician, Instant start, Instant end) {
        this.technician = technician;
        this.scheduledStart = start;
        this.scheduledEnd = end;
    }

    public Long getId() { return id; }
    public String getReference() { return reference; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public City getCity() { return city; }
    public void setCity(City city) { this.city = city; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public PropertyType getPropertyType() { return propertyType; }
    public void setPropertyType(PropertyType propertyType) { this.propertyType = propertyType; }
    public ServiceType getServiceType() { return serviceType; }
    public LocalDate getPreferredDate() { return preferredDate; }
    public void setPreferredDate(LocalDate preferredDate) { this.preferredDate = preferredDate; }
    public TimeSlot getPreferredSlot() { return preferredSlot; }
    public void setPreferredSlot(TimeSlot preferredSlot) { this.preferredSlot = preferredSlot; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public RequestStatus getStatus() { return status; }
    public AppUser getTechnician() { return technician; }
    public Instant getScheduledStart() { return scheduledStart; }
    public Instant getScheduledEnd() { return scheduledEnd; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getCompletionNote() { return completionNote; }
    public void setCompletionNote(String completionNote) { this.completionNote = completionNote; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public Quote getQuote() { return quote; }
    public void setQuote(Quote quote) { this.quote = quote; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
