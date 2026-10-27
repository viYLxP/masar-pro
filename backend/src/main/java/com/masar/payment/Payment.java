package com.masar.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.masar.request.ServiceRequest;

/**
 * One payment attempt for a request: online by card (through a gateway) or cash collected by the technician.
 * محاولة دفع وحدة لطلب: أونلاين بالبطاقة (عن طريق بوابة) أو كاش يستلمه الفني.
 *
 * <p>A failed card attempt stays in the table, so the office can see that the customer tried. Only one payment per
 * request can be PAID (a partial unique index in the database guarantees it).</p>
 * <p>محاولة البطاقة الفاشلة تبقى في الجدول، حتى يشوف المكتب إن العميل حاول. ودفعة وحدة بس لكل طلب تقدر تكون
 * PAID (فهرس فريد جزئي في قاعدة البيانات يضمن هالشي).</p>
 */
@Entity
@Table(name = "payment")
public class Payment {

    // APPLE_PAY goes through the card gateway's own page; TABBY and TAMARA split the amount into 4 instalments
    // (the shop is paid in full, the customer pays the instalment company).
    // APPLE_PAY يمر من صفحة بوابة البطاقات نفسها؛ وTABBY وTAMARA يقسمون المبلغ 4 دفعات (المتجر يستلم المبلغ
    // كامل، والعميل يسدد لشركة التقسيط).
    public enum Method {
        CARD, APPLE_PAY, TABBY, TAMARA, CASH;

        public boolean isInstalments() {
            return this == TABBY || this == TAMARA;
        }
    }

    public enum Provider { SANDBOX, MOYASAR, STRIPE, TABBY, TAMARA, CASH }

    public enum Status { PENDING, PAID, FAILED, REFUNDED }

    // Random, not a sequence: it is part of the payment page link, so it must not be guessable.
    // عشوائي مو متسلسل: هو جزء من رابط صفحة الدفع، فلازم ما ينخمّن.
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id")
    private ServiceRequest request;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Method method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Provider provider;

    // The gateway's own id (Moyasar invoice, Stripe session, Tabby payment, Tamara order).
    // رقم العملية عند البوابة (فاتورة Moyasar، جلسة Stripe، دفعة تابي، طلب تمارا).
    @Column(name = "provider_ref", length = 200)
    private String providerRef;

    @Column(name = "checkout_url", length = 1000)
    private String checkoutUrl;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "SAR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "card_brand", length = 20)
    private String cardBrand;

    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    // Staff username for cash, "customer" for online payments. / اسم الموظف للكاش، و"customer" للدفع أونلاين.
    @Column(name = "recorded_by", length = 50)
    private String recordedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    protected Payment() {
    }

    public Payment(ServiceRequest request, Method method, Provider provider, BigDecimal amount, String recordedBy,
                   Instant createdAt) {
        this.id = UUID.randomUUID();
        this.request = request;
        this.method = method;
        this.provider = provider;
        this.amount = amount;
        this.recordedBy = recordedBy;
        this.createdAt = createdAt;
    }

    public void markPaid(Instant at, String brand, String last4) {
        this.status = Status.PAID;
        this.paidAt = at;
        this.failureReason = null;
        this.cardBrand = brand;
        this.cardLast4 = last4;
    }

    public void markFailed(String reason, String brand, String last4) {
        this.status = Status.FAILED;
        this.failureReason = reason;
        this.cardBrand = brand;
        this.cardLast4 = last4;
    }

    public void markRefunded(Instant at) {
        this.status = Status.REFUNDED;
        this.refundedAt = at;
    }

    public UUID getId() { return id; }
    public ServiceRequest getRequest() { return request; }
    public Method getMethod() { return method; }
    public Provider getProvider() { return provider; }
    public String getProviderRef() { return providerRef; }
    public void setProviderRef(String providerRef) { this.providerRef = providerRef; }
    public String getCheckoutUrl() { return checkoutUrl; }
    public void setCheckoutUrl(String checkoutUrl) { this.checkoutUrl = checkoutUrl; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Status getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public String getCardBrand() { return cardBrand; }
    public String getCardLast4() { return cardLast4; }
    public String getRecordedBy() { return recordedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getRefundedAt() { return refundedAt; }
}
