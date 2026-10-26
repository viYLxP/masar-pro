package com.masar.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.masar.common.Money;
import com.masar.common.Phones;
import com.masar.request.ReferenceGenerator;
import com.masar.request.RequestStatus;
import com.masar.security.RateLimiter;

/**
 * Pure rules that need no database.
 * قواعد صافية ما تحتاج قاعدة بيانات.
 */
class DomainRulesTest {

    @Test
    void saudiMobileNumbersAreNormalised() {
        for (String typed : new String[]{"0551234567", "551234567", "+966551234567", "00966551234567",
                "966 55 123 4567", "(055) 123-4567"}) {
            assertThat(Phones.normalize(typed)).as(typed).isEqualTo("966551234567");
        }
        // Landlines, short numbers and other countries are refused. / الأرضي والقصير والدول الثانية مرفوضة.
        for (String typed : new String[]{"0112345678", "05512345", "+971551234567", "abc", "", null}) {
            assertThat(Phones.normalize(typed)).as(String.valueOf(typed)).isNull();
        }
    }

    @Test
    void vatIsRoundedHalfUpOnce() {
        assertThat(Money.vat(new BigDecimal("100.00"))).isEqualByComparingTo("15.00");
        assertThat(Money.vat(new BigDecimal("0.30"))).isEqualByComparingTo("0.05");
        assertThat(Money.vat(new BigDecimal("0.10"))).isEqualByComparingTo("0.02");
        assertThat(Money.vat(BigDecimal.ZERO)).isEqualByComparingTo("0.00");
    }

    @Test
    void statusMovesFollowTheTable() {
        assertThat(RequestStatus.NEW.canMoveTo(RequestStatus.QUOTED)).isTrue();
        assertThat(RequestStatus.NEW.canMoveTo(RequestStatus.SCHEDULED)).isFalse();
        assertThat(RequestStatus.QUOTED.canMoveTo(RequestStatus.ACCEPTED)).isTrue();
        assertThat(RequestStatus.ACCEPTED.canMoveTo(RequestStatus.COMPLETED)).isFalse();
        assertThat(RequestStatus.SCHEDULED.canMoveTo(RequestStatus.SCHEDULED)).isTrue();
        // A job on site can only finish; it cannot be cancelled half-way. / الشغل في الموقع يخلص بس؛ ما ينلغى بنصه.
        assertThat(RequestStatus.IN_PROGRESS.canMoveTo(RequestStatus.CANCELLED)).isFalse();
        assertThat(RequestStatus.COMPLETED.isFinal()).isTrue();
        assertThat(RequestStatus.CANCELLED.isFinal()).isTrue();
        assertThat(RequestStatus.REJECTED.isFinal()).isFalse();
    }

    @Test
    void referencesAreRandomAndAvoidLookAlikeCharacters() {
        ReferenceGenerator generator = new ReferenceGenerator();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 2000; i++) {
            String ref = generator.next();
            assertThat(ref).matches("MS-[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{8}");
            seen.add(ref);
        }
        assertThat(seen).hasSize(2000);
        assertThat(ReferenceGenerator.clean(" ms-abc ")).isEqualTo("MS-ABC");
    }

    @Test
    void rateLimiterAllowsNPerWindowThenTellsWhenToRetry() {
        AtomicLong now = new AtomicLong(1_000_000);
        RateLimiter limiter = new RateLimiter(2, Duration.ofMinutes(10), now::get);
        assertThat(limiter.hit("1.2.3.4")).isZero();
        assertThat(limiter.hit("1.2.3.4")).isZero();
        assertThat(limiter.hit("1.2.3.4")).isEqualTo(600);
        // Other visitors are not affected. / الزوار الثانيين ما يتأثرون.
        assertThat(limiter.hit("5.6.7.8")).isZero();
        now.addAndGet(Duration.ofMinutes(9).toMillis() + 1);
        assertThat(limiter.hit("1.2.3.4")).isEqualTo(60);
        // A new window starts after 10 minutes. / تبدأ فترة جديدة بعد 10 دقايق.
        now.addAndGet(Duration.ofMinutes(1).toMillis());
        assertThat(limiter.hit("1.2.3.4")).isZero();
    }

    @Test
    void quoteExpiresTheDayAfterValidUntil() {
        var quote = new com.masar.request.Quote(null);
        quote.setValidUntil(LocalDate.of(2026, 10, 26));
        assertThat(quote.isExpired(LocalDate.of(2026, 10, 26))).isFalse();
        assertThat(quote.isExpired(LocalDate.of(2026, 10, 27))).isTrue();
    }
}
