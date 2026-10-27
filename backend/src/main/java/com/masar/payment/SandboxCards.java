package com.masar.payment;

import java.time.YearMonth;
import java.util.List;

/**
 * Test card rules for the sandbox checkout, in the style real gateways use for their test mode.
 * قواعد البطاقات التجريبية لصفحة الدفع التجريبية، بنفس أسلوب البوابات الحقيقية في وضع الاختبار.
 *
 * <p>Any number that passes the Luhn check and has a future expiry is approved, except two numbers that simulate
 * failures. The card number is only read here: it is never stored or logged; the payment keeps brand + last 4.</p>
 * <p>أي رقم يعدّي فحص Luhn وتاريخه ما انتهى يُقبل، إلا رقمين يمثّلون حالات فشل. رقم البطاقة ينقرأ هنا بس:
 * ما ينحفظ ولا ينكتب في السجلات؛ والدفعة تحفظ النوع وآخر 4 أرقام.</p>
 */
public final class SandboxCards {

    public static final String DECLINED = "4000000000000002";
    public static final String INSUFFICIENT_FUNDS = "4000000000009995";

    // A few mada BIN prefixes (Saudi debit cards), so a mada test card shows as "mada".
    // بعض بدايات بطاقات مدى، حتى بطاقة مدى التجريبية تبين "مدى".
    private static final List<String> MADA_BINS = List.of("588845", "440647", "446404", "457865", "968208");

    private SandboxCards() {
    }

    public record Outcome(boolean approved, String brand, String last4, String failureReason) {
    }

    /**
     * Digits only, or null when the number cannot be a card (wrong length or failed Luhn check).
     * الأرقام بس، أو null لو الرقم ما ينفع يكون بطاقة (طول غلط أو فشل فحص Luhn).
     */
    public static String clean(String number) {
        String digits = number == null ? "" : number.replaceAll("[\\s-]", "");
        if (!digits.matches("\\d{13,19}") || !luhn(digits)) {
            return null;
        }
        return digits;
    }

    public static boolean expiryValid(int month, int year, YearMonth now) {
        if (month < 1 || month > 12) {
            return false;
        }
        // Two-digit years ("28") mean 2028. / السنة بخانتين ("28") تعني 2028.
        int fullYear = year < 100 ? 2000 + year : year;
        return !YearMonth.of(fullYear, month).isBefore(now);
    }

    public static Outcome charge(String digits) {
        String brand = brand(digits);
        String last4 = digits.substring(digits.length() - 4);
        return switch (digits) {
            case DECLINED -> new Outcome(false, brand, last4, "CARD_DECLINED");
            case INSUFFICIENT_FUNDS -> new Outcome(false, brand, last4, "INSUFFICIENT_FUNDS");
            default -> new Outcome(true, brand, last4, null);
        };
    }

    static String brand(String digits) {
        if (MADA_BINS.stream().anyMatch(digits::startsWith)) {
            return "mada";
        }
        if (digits.startsWith("4")) {
            return "visa";
        }
        if (digits.matches("^(5[1-5]|2[2-7]).*")) {
            return "mastercard";
        }
        return "card";
    }

    // The standard checksum on card numbers: catches most typos. / فحص قياسي لأرقام البطاقات: يكشف أغلب أخطاء الكتابة.
    static boolean luhn(String digits) {
        int sum = 0;
        boolean twice = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (twice) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            twice = !twice;
        }
        return sum % 10 == 0;
    }
}
