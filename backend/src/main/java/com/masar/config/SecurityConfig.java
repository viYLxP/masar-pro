package com.masar.config;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.masar.security.ProblemAuthenticationEntryPoint;
import com.masar.security.ProblemAccessDeniedHandler;
import com.masar.user.AppUser;
import com.masar.user.AppUserRepository;

/**
 * Security setup: public customer endpoints, and stateless JWT authentication for staff (ADMIN, DISPATCHER, TECHNICIAN).
 * إعداد الأمان: روابط عامة للعملاء، ومصادقة JWT بدون جلسات (stateless) للموظفين (ADMIN و DISPATCHER و TECHNICIAN).
 *
 * <p>We use Spring Security's built-in OAuth2 Resource Server support to validate tokens instead of a
 * hand-written filter: it checks the signature and expiry for us and is well tested.</p>
 * <p>نستخدم دعم OAuth2 Resource Server المدمج في Spring Security للتحقق من الرموز بدل كتابة فلتر يدوي:
 * يتحقق من التوقيع والانتهاء بنفسه ومختبر جيدًا.</p>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    // Name of the JWT claim that holds the user's role.
    // اسم الحقل داخل رمز JWT الذي يحمل صلاحية المستخدم.
    public static final String ROLE_CLAIM = "role";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ProblemAuthenticationEntryPoint entryPoint,
                                            ProblemAccessDeniedHandler accessDeniedHandler,
                                            AppUserRepository users) throws Exception {
        http
                // CSRF protection is for cookie sessions; we send the token in the Authorization header, so it is not needed.
                // حماية CSRF مخصصة لجلسات الكوكيز، ونحن نرسل الرمز في ترويسة Authorization فلا نحتاجها.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {
                })
                // Never create an HTTP session: every request must carry its own token.
                // لا ننشئ جلسة HTTP أبدًا: كل طلب لازم يحمل رمزه.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints: login, health check and the API docs.
                        // روابط عامة: تسجيل الدخول، فحص الصحة، وتوثيق الـ API.
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // The customer side of the site: services, booking, tracking and answering the quote.
                        // الجهة العامة للموقع: الخدمات، الحجز، التتبع، والرد على عرض السعر.
                        .requestMatchers("/api/public/**").permitAll()
                        // User management is admin-only.
                        // إدارة المستخدمين للمدير فقط.
                        .requestMatchers("/api/users/**").hasRole("ADMIN")
                        // Everything else under /api needs a valid token.
                        // باقي روابط /api تحتاج رمزًا صالحًا.
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter(users)))
                        // Return our ProblemDetail JSON instead of an empty 401 body.
                        // نرجّع JSON بصيغة ProblemDetail بدل رد 401 فاضي.
                        .authenticationEntryPoint(entryPoint))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    /**
     * Turns a valid token into the logged-in user, using the account as it is NOW in the database.
     * يحوّل الرمز الصالح للمستخدم المسجّل، بحسب حالة الحساب الحين في قاعدة البيانات.
     *
     * <p>A token lives for hours. Reading the account on each request means a deactivated user is locked out
     * at once, and a role change (e.g. dispatcher → technician) applies immediately instead of when the token expires.
     * It costs one lookup on a unique index per request.</p>
     * <p>الرمز يعيش ساعات. قراءة الحساب مع كل طلب تعني إن المستخدم المعطّل ينقفل عليه فورًا، وتغيير الدور
     * (مثل موزّع ← فني) يطبق مباشرة بدل ما ينتظر انتهاء الرمز. والتكلفة استعلام واحد على فهرس فريد لكل طلب.</p>
     */
    private Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter(AppUserRepository users) {
        return jwt -> {
            AppUser user = users.findByUsernameIgnoreCase(jwt.getSubject())
                    .filter(AppUser::isActive)
                    // An AuthenticationException becomes a 401, so the UI sends the user back to the login page.
                    // AuthenticationException يتحول لـ 401، فالواجهة ترجّع المستخدم لصفحة الدخول.
                    .orElseThrow(() -> new DisabledException("Account is inactive or was removed"));
            return new JwtAuthenticationToken(jwt,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())), user.getUsername());
        };
    }

    /**
     * Builds the HMAC-SHA256 signing key from the configured secret.
     * يبني مفتاح التوقيع HMAC-SHA256 من السر الموجود في الإعدادات.
     */
    @Bean
    SecretKey jwtSecretKey(AppProperties properties) {
        byte[] bytes = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    // Signs new tokens at login.
    // يوقّع الرموز الجديدة عند تسجيل الدخول.
    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    // Verifies the signature and expiry of incoming tokens.
    // يتحقق من توقيع وانتهاء الرموز القادمة مع كل طلب.
    @Bean
    JwtDecoder jwtDecoder(SecretKey key) {
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    // BCrypt hashes passwords with a random salt; plain passwords are never stored.
    // BCrypt يشفّر كلمات المرور مع salt عشوائي، ولا نخزّن كلمة المرور الأصلية أبدًا.
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Checks username + password against the database during login.
    // يتحقق من اسم المستخدم وكلمة المرور مقابل قاعدة البيانات عند الدخول.
    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    // Lets the Angular app (another origin in development) call the API from the browser.
    // يسمح لتطبيق Angular (مصدر مختلف أثناء التطوير) بالاتصال بالـ API من المتصفح.
    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept-Language"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
