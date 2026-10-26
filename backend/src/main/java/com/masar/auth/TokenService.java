package com.masar.auth;

import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.masar.config.AppProperties;
import com.masar.config.SecurityConfig;
import com.masar.user.AppUser;

/**
 * Issues signed JWT access tokens.
 * يصدر رموز JWT موقّعة للدخول.
 */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final AppProperties properties;

    public TokenService(JwtEncoder encoder, AppProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    /**
     * Creates a token for the user.
     * ينشئ رمزًا للمستخدم.
     *
     * @return the token and its expiry instant / الرمز ووقت انتهائه
     */
    public IssuedToken issue(AppUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.jwt().expiration());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("masar-api")
                .issuedAt(now)
                .expiresAt(expiresAt)
                // "sub" (subject) holds the username; Spring exposes it as Authentication.getName().
                // "sub" يحمل اسم المستخدم، وSpring يعرضه كـ Authentication.getName().
                .subject(user.getUsername())
                .claim(SecurityConfig.ROLE_CLAIM, user.getRole().name())
                .build();
        // HS256 must be set explicitly in the header, otherwise Nimbus cannot pick an algorithm for a secret key.
        // لازم نحدد HS256 صراحة في الترويسة، وإلا ما يقدر Nimbus يختار خوارزمية للمفتاح السري.
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
