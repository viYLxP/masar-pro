package com.masar.security;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * A small fixed-window counter: at most N hits per key (e.g. per IP) in each window.
 * عدّاد بسيط بنافذة ثابتة: بالكثير N مرة لكل مفتاح (مثل كل IP) في كل فترة.
 *
 * <p>It lives in memory, which is enough for one API instance. Running several instances would need a shared store
 * (e.g. Redis) instead; that is noted in the README.</p>
 * <p>يعيش في الذاكرة، وهذا يكفي لنسخة API وحدة. لو تشغّل أكثر من نسخة تحتاج مخزن مشترك
 * (مثل Redis) بداله؛ وهذا مكتوب في README.</p>
 */
public class RateLimiter {

    // Above this many keys, expired windows are swept so memory cannot grow without limit under an attack.
    // لو زادت المفاتيح عن هذا العدد ننظف الفترات المنتهية، حتى الذاكرة ما تكبر بلا حد وقت الهجوم.
    private static final int SWEEP_THRESHOLD = 10_000;

    private record Window(long startedAt, int count) {
    }

    private final int limit;
    private final long windowMillis;
    private final LongSupplier clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(int limit, Duration window, LongSupplier clock) {
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    /**
     * Counts one hit. Returns 0 if allowed, otherwise the seconds until the key may try again.
     * يحسب محاولة وحدة. يرجّع 0 لو مسموحة، وإلا عدد الثواني لين يقدر يحاول مرة ثانية.
     */
    public long hit(String key) {
        long now = clock.getAsLong();
        if (windows.size() > SWEEP_THRESHOLD) {
            windows.values().removeIf(w -> now - w.startedAt() >= windowMillis);
        }
        // compute() runs atomically per key, so two parallel requests cannot both take the last free hit.
        // compute() ينفّذ بشكل ذري لكل مفتاح، فطلبين متوازيين ما يقدرون ياخذون آخر محاولة مع بعض.
        Window w = windows.compute(key, (k, old) -> {
            if (old == null || now - old.startedAt() >= windowMillis) {
                return new Window(now, 1);
            }
            return new Window(old.startedAt(), old.count() + 1);
        });
        if (w.count() <= limit) {
            return 0;
        }
        // Round up so the client never retries a moment too early. / نقرّب لأعلى حتى العميل ما يعيد بدري بلحظة.
        return Math.max(1, (w.startedAt() + windowMillis - now + 999) / 1000);
    }
}
