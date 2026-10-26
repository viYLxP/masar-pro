package com.masar.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money helpers. Amounts are in SAR with 2 decimals (halalas).
 * دوال المبالغ. المبالغ بالريال بمنزلتين عشريتين (هللات).
 */
public final class Money {

    // Saudi VAT rate (15%). / نسبة ضريبة القيمة المضافة في السعودية (15%).
    public static final BigDecimal VAT_RATE = new BigDecimal("0.15");

    private Money() {
    }

    /**
     * VAT on a subtotal, rounded once on the total (half-up), the way Saudi invoices are usually computed.
     * Rounding each line separately could make the sum differ from the total by a halala.
     * الضريبة على المجموع، مقرّبة مرة وحدة على الإجمالي (نص فوق)، مثل ما تنحسب الفواتير عادة في السعودية.
     * تقريب كل بند لحاله ممكن يخلي المجموع يختلف عن الإجمالي بهللة.
     */
    public static BigDecimal vat(BigDecimal subtotal) {
        return subtotal.multiply(VAT_RATE).setScale(2, RoundingMode.HALF_UP);
    }
}
