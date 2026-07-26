package com.vdmytriv.carsharing.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class JwtService {

    private final Duration expiration;
    private final String issuer;
    private final JwtParser jwtParser;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        validateExpiration(properties.expiration());
        expiration = properties.expiration();
        issuer = properties.issuer();
        signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
        jwtParser = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build();
    }

    public String generateToken(String email) {
        Instant issuedAt = Instant.now();
        return Jwts.builder()
                .subject(email)
                .issuer(issuer)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(expiration)))
                .signWith(signingKey)
                .compact();
    }

    public String extractEmail(String token) {
        Claims claims = jwtParser.parseSignedClaims(token).getPayload();
        if (!StringUtils.hasText(claims.getSubject())) {
            throw new JwtException("JWT subject is missing");
        }
        if (claims.getExpiration() == null) {
            throw new JwtException("JWT expiration is missing");
        }
        return claims.getSubject();
    }

    private void validateExpiration(Duration tokenExpiration) {
        if (tokenExpiration.isZero() || tokenExpiration.isNegative()) {
            throw new IllegalArgumentException("JWT expiration must be positive");
        }
    }
}
