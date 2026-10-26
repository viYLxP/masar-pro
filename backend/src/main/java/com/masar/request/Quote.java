package com.masar.request;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.masar.common.Money;

/**
 * The price offer for one request: lines, 15% VAT and a "valid until" date.
 * عرض السعر لطلب واحد: البنود، وضريبة 15%، وتاريخ انتهاء الصلاحية.
 */
@Entity
@Table(name = "quote")
public class Quote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id")
    private ServiceRequest request;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal vat = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(length = 1000)
    private String notes;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<QuoteLine> lines = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Quote() {
    }

    public Quote(ServiceRequest request) {
        this.request = request;
    }

    /**
     * Removes all lines. The caller flushes before adding new ones: (quote_id, position) is unique, and Hibernate
     * runs inserts before deletes, so the new line 1 would clash with the old line 1 still in the table.
     * يحذف كل البنود. اللي يستدعيه يسوي flush قبل إضافة الجديدة: (quote_id, position) فريد، وHibernate ينفّذ
     * الإضافة قبل الحذف، فالبند 1 الجديد بيتعارض مع البند 1 القديم اللي لسا في الجدول.
     */
    public void clearLines() {
        lines.clear();
    }

    /**
     * Adds the lines in order and recomputes the totals, so the stored totals always match the lines.
     * يضيف البنود بالترتيب ويعيد حساب الإجماليات، حتى الإجماليات المحفوظة تطابق البنود دائمًا.
     */
    public void addLines(List<QuoteLine> newLines) {
        int position = lines.size() + 1;
        for (QuoteLine line : newLines) {
            line.attach(this, position++);
            lines.add(line);
        }
        subtotal = lines.stream().map(QuoteLine::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        vat = Money.vat(subtotal);
        total = subtotal.add(vat);
    }

    /**
     * Expired once the business day after "valid until" has started.
     * ينتهي أول ما يبدأ يوم العمل اللي بعد تاريخ الصلاحية.
     */
    public boolean isExpired(LocalDate today) {
        return today.isAfter(validUntil);
    }

    public Long getId() { return id; }
    public ServiceRequest getRequest() { return request; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getVat() { return vat; }
    public BigDecimal getTotal() { return total; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    public Instant getRespondedAt() { return respondedAt; }
    public void setRespondedAt(Instant respondedAt) { this.respondedAt = respondedAt; }
    public List<QuoteLine> getLines() { return lines; }
}
