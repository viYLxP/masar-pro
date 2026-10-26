package com.masar.request;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One priced line of a quote (e.g. "Split AC deep clean × 3").
 * بند واحد مسعّر في عرض السعر (مثل "تنظيف مكيف سبليت × 3").
 */
@Entity
@Table(name = "quote_line")
public class QuoteLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_id")
    private Quote quote;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    protected QuoteLine() {
    }

    public QuoteLine(String description, int quantity, BigDecimal unitPrice) {
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        // Quantity × price is exact with 2 decimals, so no rounding is needed here.
        // الكمية × السعر ناتجها دقيق بمنزلتين، فما نحتاج تقريب هنا.
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    void attach(Quote quote, int position) {
        this.quote = quote;
        this.position = position;
    }

    public Long getId() { return id; }
    public int getPosition() { return position; }
    public String getDescription() { return description; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getLineTotal() { return lineTotal; }
}
