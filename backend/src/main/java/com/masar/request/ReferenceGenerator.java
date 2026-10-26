package com.masar.request;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/**
 * Makes public request references like "MS-7K3QX9PA".
 * يصنع الأرقام المرجعية العامة مثل "MS-7K3QX9PA".
 *
 * <p>8 random characters from a 31-letter alphabet give about 850 billion values, so guessing a valid reference
 * (and then also its phone number) is not practical. Letters that look alike (0/O, 1/I/L) are left out, because
 * customers read the reference out on the phone.</p>
 * <p>8 أحرف عشوائية من 31 حرف تعطي تقريبًا 850 مليار احتمال، فتخمين رقم صحيح (ومعه رقم جواله) غير عملي.
 * والحروف المتشابهة (0/O و 1/I/L) محذوفة، لأن العملاء يقرون الرقم بالتلفون.</p>
 */
@Component
public class ReferenceGenerator {

    static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    static final int LENGTH = 8;

    // SecureRandom, not Random: java.util.Random is predictable from a few outputs.
    // SecureRandom وليس Random: لأن java.util.Random ممكن يتوقع من كم ناتج.
    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder sb = new StringBuilder("MS-");
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * Upper-cases and trims what the customer typed, so "ms-7k3qx9pa " still matches.
     * يحوّل للحروف الكبيرة ويحذف المسافات من اللي كتبه العميل، فـ "ms-7k3qx9pa " يطابق.
     */
    public static String clean(String typed) {
        return typed == null ? "" : typed.trim().toUpperCase();
    }
}
