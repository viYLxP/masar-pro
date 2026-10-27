package com.masar.payment;

import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * The built-in test checkout: a page in our own app that accepts test card numbers. No money moves.
 * صفحة الدفع التجريبية المدمجة: صفحة داخل تطبيقنا تقبل أرقام بطاقات تجريبية. ما تتحرك أي فلوس.
 *
 * <p>It lets the project run end to end without a gateway account, and the automated tests use it. The card rules
 * are in {@link SandboxCards}.</p>
 * <p>يخلّي المشروع يشتغل من أوله لآخره بدون حساب بوابة، والاختبارات الآلية تستخدمه. قواعد البطاقات في
 * {@link SandboxCards}.</p>
 */
@Component
public class SandboxGateway implements PaymentGateway {

    @Override
    public Payment.Provider provider() {
        return Payment.Provider.SANDBOX;
    }

    @Override
    public Checkout start(Payment payment, Urls urls) {
        // Our own Angular page /pay/<id> plays the gateway's hosted page. / صفحة Angular حقتنا /pay/<id> تلعب دور صفحة البوابة.
        return new Checkout("sbx_" + payment.getId(), urls.checkoutPage());
    }

    @Override
    public Optional<Result> fetch(Payment payment) {
        return Optional.empty();
    }

    @Override
    public void refund(Payment payment) {
        // Nothing to call: no real money was taken. / ما فيه شي نتصل فيه: ما انسحبت فلوس حقيقية.
    }
}
