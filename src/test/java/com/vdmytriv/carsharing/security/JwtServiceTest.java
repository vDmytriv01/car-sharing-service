package com.vdmytriv.carsharing.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final Duration EXPIRATION = Duration.ofHours(1);
    private static final String ISSUER = "car-sharing-service-test";
    private static final String SECRET =
            "dGVzdC1qd3Qtc2lnbmluZy1rZXktbXVzdC1iZS0zMi1ieXRlcy1sb25n";

    private final JwtService jwtService =
            new JwtService(new JwtProperties(SECRET, ISSUER, EXPIRATION));
    private final SecretKey signingKey =
            Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));

    @Test
    void generateToken_WithUsername_CreatesValidToken() {
        String token = jwtService.generateToken("customer@example.com");

        assertThat(jwtService.extractEmail(token)).isEqualTo("customer@example.com");
    }

    @Test
    void extractUsername_WithExpiredToken_ThrowsException() {
        Instant now = Instant.now();
        String expiredToken = Jwts.builder()
                .subject("customer@example.com")
                .issuer(ISSUER)
                .issuedAt(Date.from(now.minus(Duration.ofHours(2))))
                .expiration(Date.from(now.minus(Duration.ofHours(1))))
                .signWith(signingKey)
                .compact();

        assertThatThrownBy(() -> jwtService.extractEmail(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void extractUsername_WithoutSubject_ThrowsException() {
        String token = Jwts.builder()
                .issuer(ISSUER)
                .expiration(Date.from(Instant.now().plus(EXPIRATION)))
                .signWith(signingKey)
                .compact();

        assertThatThrownBy(() -> jwtService.extractEmail(token))
                .isInstanceOf(JwtException.class)
                .hasMessage("JWT subject is missing");
    }

    @Test
    void extractUsername_WithoutExpiration_ThrowsException() {
        String token = Jwts.builder()
                .subject("customer@example.com")
                .issuer(ISSUER)
                .signWith(signingKey)
                .compact();

        assertThatThrownBy(() -> jwtService.extractEmail(token))
                .isInstanceOf(JwtException.class)
                .hasMessage("JWT expiration is missing");
    }

    @Test
    void constructor_WithNonPositiveExpiration_ThrowsException() {
        JwtProperties properties = new JwtProperties(SECRET, ISSUER, Duration.ZERO);

        assertThatThrownBy(() -> new JwtService(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT expiration must be positive");
    }
}
