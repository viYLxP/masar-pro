package com.masar.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.masar.config.AppProperties;

/**
 * Limits the public POST endpoints per visitor IP: new bookings (spam) and lookups (guessing phone numbers).
 * يحدّ روابط POST العامة لكل عنوان IP: الحجوزات الجديدة (الإزعاج) وعمليات البحث (تخمين أرقام الجوال).
 *
 * <p>The IP comes from request.getRemoteAddr(). With server.forward-headers-strategy=native, Tomcat replaces it with
 * the X-Forwarded-For value only when the request came through a trusted internal proxy (our nginx).</p>
 * <p>العنوان من request.getRemoteAddr(). ومع server.forward-headers-strategy=native، Tomcat يبدله بقيمة
 * X-Forwarded-For بس لو الطلب جا من بروكسي داخلي موثوق (nginx حقنا).</p>
 */
@Component
public class PublicRateLimitFilter extends OncePerRequestFilter {

    private static final String BOOK_PATH = "/api/public/requests";
    private static final String LOOKUP_PREFIX = "/api/public/requests/";
    // Sign-ups count as bookings (both create records); card attempts count as lookups (guessing card numbers).
    // التسجيل ينحسب مع الحجوزات (الاثنين ينشئون سجلات)؛ ومحاولات البطاقة تنحسب مع البحث (تخمين أرقام البطاقات).
    private static final String REGISTER_PATH = "/api/auth/register";
    private static final String PAYMENTS_PREFIX = "/api/public/payments/";

    private final RateLimiter bookings;
    private final RateLimiter lookups;

    public PublicRateLimitFilter(AppProperties properties) {
        AppProperties.RateLimit config = properties.rateLimit();
        this.bookings = new RateLimiter(config.bookings(), config.window(), System::currentTimeMillis);
        this.lookups = new RateLimiter(config.lookups(), config.window(), System::currentTimeMillis);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !HttpMethod.POST.matches(request.getMethod())
                || !(path.startsWith(BOOK_PATH) || path.equals(REGISTER_PATH) || path.startsWith(PAYMENTS_PREFIX));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        RateLimiter limiter = path.startsWith(LOOKUP_PREFIX) || path.startsWith(PAYMENTS_PREFIX) ? lookups : bookings;
        long retryAfter = limiter.hit(request.getRemoteAddr());
        if (retryAfter > 0) {
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(retryAfter));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"status\":429,\"title\":\"Too Many Requests\",\"code\":\"RATE_LIMITED\","
                    + "\"retryAfterSeconds\":" + retryAfter + "}");
            return;
        }
        chain.doFilter(request, response);
    }
}
