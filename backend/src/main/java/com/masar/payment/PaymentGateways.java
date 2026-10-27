package com.masar.payment;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.masar.common.ApiException;
import com.masar.common.ErrorCode;
import com.masar.config.AppProperties;

/**
 * Which gateway handles each payment method, built from the settings.
 * أي بوابة تعالج كل طريقة دفع، مبنية من الإعدادات.
 *
 * <ul>
 *   <li>Card and Apple Pay: the configured card provider (SANDBOX, MOYASAR or STRIPE). Both real gateways show
 *   Apple Pay on their own hosted page. / البطاقة وApple Pay: مزوّد البطاقات المختار (SANDBOX أو MOYASAR أو
 *   STRIPE). البوابتين الحقيقيتين يعرضون Apple Pay في صفحتهم.</li>
 *   <li>Tabby and Tamara: their real API when a key is set, else the sandbox, so the whole flow still works in a
 *   demo. / تابي وتمارا: الـ API الحقيقي لو فيه مفتاح، وإلا الوضع التجريبي، فالمسار كامل يشتغل في العرض.</li>
 * </ul>
 *
 * <p>The sandbox is always registered, so payments made in test mode can still be read after switching to a real
 * gateway. Choosing MOYASAR or STRIPE without its key stops the app from starting (better than failing at the
 * customer's first payment).</p>
 * <p>الوضع التجريبي مسجّل دايم، فالدفعات اللي انعملت فيه تنقرأ حتى بعد التحويل لبوابة حقيقية. واختيار MOYASAR أو
 * STRIPE بدون مفتاحه يمنع التطبيق من التشغيل (أحسن من الفشل عند أول دفعة للعميل).</p>
 */
@Component
public class PaymentGateways {

    // The methods offered online, in the order the customer sees them. / الطرق المتاحة أونلاين، بالترتيب اللي يشوفه العميل.
    public static final List<Payment.Method> ONLINE = List.of(Payment.Method.CARD, Payment.Method.APPLE_PAY,
            Payment.Method.TABBY, Payment.Method.TAMARA);

    private final Map<Payment.Provider, PaymentGateway> byProvider = new EnumMap<>(Payment.Provider.class);
    private final Map<Payment.Method, Payment.Provider> byMethod = new EnumMap<>(Payment.Method.class);

    public PaymentGateways(AppProperties properties, SandboxGateway sandbox) {
        AppProperties.Payments config = properties.payments();
        byProvider.put(Payment.Provider.SANDBOX, sandbox);
        if (has(config.moyasar().secretKey())) {
            byProvider.put(Payment.Provider.MOYASAR, new MoyasarGateway(RestClient.builder(),
                    config.moyasar().apiUrl(), config.moyasar().secretKey().trim()));
        }
        if (has(config.stripe().secretKey())) {
            byProvider.put(Payment.Provider.STRIPE, new StripeGateway(RestClient.builder(),
                    config.stripe().apiUrl(), config.stripe().secretKey().trim()));
        }
        if (has(config.tabby().secretKey()) && has(config.tabby().merchantCode())) {
            byProvider.put(Payment.Provider.TABBY, new TabbyGateway(RestClient.builder(), config.tabby().apiUrl(),
                    config.tabby().secretKey().trim(), config.tabby().merchantCode().trim()));
        }
        if (has(config.tamara().apiToken())) {
            byProvider.put(Payment.Provider.TAMARA, new TamaraGateway(RestClient.builder(), config.tamara().apiUrl(),
                    config.tamara().apiToken().trim(), config.instalments().count()));
        }
        Payment.Provider card = config.cardProvider();
        if (!byProvider.containsKey(card) || card == Payment.Provider.TABBY || card == Payment.Provider.TAMARA) {
            throw new IllegalStateException("CARD_PAYMENT_PROVIDER=" + card + " is not configured "
                    + "(MOYASAR needs MOYASAR_SECRET_KEY, STRIPE needs STRIPE_SECRET_KEY)");
        }
        byMethod.put(Payment.Method.CARD, card);
        byMethod.put(Payment.Method.APPLE_PAY, card);
        byMethod.put(Payment.Method.TABBY, byProvider.containsKey(Payment.Provider.TABBY)
                ? Payment.Provider.TABBY : Payment.Provider.SANDBOX);
        byMethod.put(Payment.Method.TAMARA, byProvider.containsKey(Payment.Provider.TAMARA)
                ? Payment.Provider.TAMARA : Payment.Provider.SANDBOX);
    }

    public PaymentGateway forMethod(Payment.Method method) {
        Payment.Provider provider = byMethod.get(method);
        if (provider == null) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_ALLOWED);
        }
        return byProvider.get(provider);
    }

    public PaymentGateway of(Payment.Provider provider) {
        PaymentGateway gateway = byProvider.get(provider);
        if (gateway == null) {
            throw new ApiException(ErrorCode.PAYMENT_PROVIDER_ERROR);
        }
        return gateway;
    }

    private static boolean has(String value) {
        return value != null && !value.isBlank();
    }
}
