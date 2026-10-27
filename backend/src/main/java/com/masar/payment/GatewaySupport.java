package com.masar.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientException;

import com.masar.common.ApiException;
import com.masar.common.ErrorCode;

/**
 * Small helpers shared by the real gateway adapters.
 * أدوات صغيرة مشتركة بين محوّلات البوابات الحقيقية.
 */
final class GatewaySupport {

    private static final Logger log = LoggerFactory.getLogger(GatewaySupport.class);

    private GatewaySupport() {
    }

    /**
     * Any network or HTTP error becomes one clear API error; the details go to the log, not to the customer.
     * أي خطأ شبكة أو HTTP يصير خطأ API واحد واضح؛ والتفاصيل تروح للسجل، مو للعميل.
     */
    static <T> T call(String gateway, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientException e) {
            log.warn("{} call failed: {}", gateway, e.getMessage());
            throw new ApiException(ErrorCode.PAYMENT_PROVIDER_ERROR);
        }
    }

    // Gateways count money in the smallest unit: 345.00 SAR = 34500 halalas. / البوابات تحسب بالهللة: 345.00 ريال = 34500.
    static int halalas(BigDecimal amount) {
        return amount.movePointRight(2).intValueExact();
    }

    // "345.00", the format Tabby expects. / "345.00"، الصيغة اللي تبيها تابي.
    static String decimal(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    // Whether two amounts are the same money (345 and 345.00 are). / هل المبلغين نفس الفلوس (345 و345.00 نفسهم).
    static boolean same(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.compareTo(b) == 0;
    }
}
