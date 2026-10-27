package com.masar.payment;

import java.util.Optional;

/**
 * A card payment provider. Each provider is one implementation, so adding Stripe or another gateway later
 * does not touch the booking or payment rules.
 * مزوّد دفع بالبطاقة. كل مزوّد له تطبيق واحد، فإضافة Stripe أو بوابة ثانية لاحقًا ما تلمس قواعد الحجز أو الدفع.
 */
public interface PaymentGateway {

    Payment.Provider provider();

    /**
     * Opens a checkout for the payment and returns where to send the customer.
     * يفتح صفحة دفع للعملية ويرجّع الرابط اللي ننقل له العميل.
     */
    Checkout start(Payment payment, Urls urls);

    /**
     * Asks the provider what happened. Empty when the provider has nothing to add (the sandbox records results itself).
     * يسأل المزوّد وش صار. فاضي لو المزوّد ما عنده شي (الوضع التجريبي يسجّل النتيجة بنفسه).
     */
    Optional<Result> fetch(Payment payment);

    // Returns the money of a paid payment. / يرجّع مبلغ دفعة مدفوعة.
    void refund(Payment payment);

    record Checkout(String providerRef, String url) {
    }

    // checkoutPage: our own payment page (used by the sandbox); successUrl: where the gateway sends the customer
    // after paying; backUrl: where "back" goes.
    // checkoutPage: صفحة الدفع حقتنا (يستخدمها الوضع التجريبي)؛ successUrl: وين ترجّع البوابة العميل بعد الدفع؛
    // وbackUrl: وين يروح زر "رجوع".
    record Urls(String checkoutPage, String successUrl, String backUrl) {

        // Instalment companies ask for a separate "failed" page; ours shows the result either way.
        // شركات التقسيط تطلب صفحة "فشل" منفصلة؛ وصفحتنا تعرض النتيجة في الحالتين.
        public String failureUrl() {
            return successUrl;
        }
    }

    record Result(Payment.Status status, String brand, String last4, String failureReason) {
    }
}
