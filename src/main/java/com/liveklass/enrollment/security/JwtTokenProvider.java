package com.liveklass.enrollment.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expirationSeconds;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret,
                             @Value("${jwt.expiration-seconds}") long expirationSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
    }

    public String createToken(String userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(key)
                .compact();
    }

    /**
     * 토큰을 한 번만 파싱/서명검증해서 유효하면 subject(userId)를 돌려준다. 검증과 subject
     * 추출을 별도 메서드로 나누면 호출부에서 같은 토큰을 두 번 파싱/검증하게 되어(인증이
     * 필요한 모든 요청마다 HMAC 서명검증이 두 번 도는 비효율) 하나로 합쳤다.
     */
    public Optional<String> resolveUserId(String token) {
        try {
            String subject = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Optional.of(subject);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
