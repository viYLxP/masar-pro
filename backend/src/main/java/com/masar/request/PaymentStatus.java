package com.masar.request;

/**
 * Whether the customer has paid for the request (online or in cash).
 * هل العميل دفع قيمة الطلب (أونلاين أو كاش).
 */
public enum PaymentStatus {
    UNPAID,
    PAID,
    // Paid online, then the request was cancelled and the money returned. / انْدفع أونلاين، بعدين انلغى الطلب ورجع المبلغ.
    REFUNDED
}
