package cn.gov.enterprise.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private static final String TOKEN_VERSION = "tokenVersion";
    private static final String REVOKED_PREFIX = "security:jwt:revoked:";
    private final JwtProperties properties;
    private final SecretKey key;
    private final StringRedisTemplate redisTemplate;

    public JwtTokenService(JwtProperties properties, StringRedisTemplate redisTemplate) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.redisTemplate = redisTemplate;
    }

    public String createToken(Long userId, int tokenVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.issuer())
                .audience().add(properties.audience()).and()
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .claim(TOKEN_VERSION, tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.expirationSeconds())))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.issuer())
                .requireAudience(properties.audience())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public int tokenVersion(Claims claims) {
        Object value = claims.get(TOKEN_VERSION);
        if (!(value instanceof Number version)) {
            throw new IllegalArgumentException("JWT tokenVersion claim is missing");
        }
        return version.intValue();
    }

    public boolean isRevoked(Claims claims) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(REVOKED_PREFIX + claims.getId()));
    }

    public void revoke(String token) {
        Claims claims = parse(token);
        Duration ttl = Duration.between(Instant.now(), claims.getExpiration().toInstant());
        if (!ttl.isNegative() && !ttl.isZero()) {
            redisTemplate.opsForValue().set(REVOKED_PREFIX + claims.getId(), "1", ttl);
        }
    }
}
