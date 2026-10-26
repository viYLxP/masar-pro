package com.masar.request;

import java.time.Instant;

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
import jakarta.persistence.Table;

/**
 * One row per status change (append-only). The first row of a request has fromStatus = null.
 * صف لكل تغيير حالة (يُضاف فقط). أول صف للطلب يكون fromStatus = null.
 */
@Entity
@Table(name = "request_event")
public class RequestEvent {

    // The actor name stored for things the customer did on the public site.
    // اسم المنفّذ اللي ينحفظ للعمليات اللي سواها العميل من الموقع العام.
    public static final String CUSTOMER = "customer";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id")
    private ServiceRequest request;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private RequestStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private RequestStatus toStatus;

    @Column(nullable = false, length = 50)
    private String actor;

    @Column(length = 1000)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RequestEvent() {
    }

    public RequestEvent(ServiceRequest request, RequestStatus fromStatus, RequestStatus toStatus, String actor,
                        String note, Instant createdAt) {
        this.request = request;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actor = actor;
        this.note = note;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public RequestStatus getFromStatus() { return fromStatus; }
    public RequestStatus getToStatus() { return toStatus; }
    public String getActor() { return actor; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
}
