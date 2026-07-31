package cn.gov.enterprise.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.mockito.Mockito.mock;

class JwtTokenServiceTest {
    @Test
    void createsAndParsesSignedToken() {
        JwtProperties properties = new JwtProperties(
                "test-secret-must-have-at-least-thirty-two-bytes",
                3600,
                "enterprise-platform-test",
                "enterprise-platform-api-test");
        JwtTokenService service = new JwtTokenService(
                properties, mock(StringRedisTemplate.class));

        String token = service.createToken(10001L, 3);
        var claims = service.parse(token);

        assertThat(claims.getSubject()).isEqualTo("10001");
        assertThat(token).doesNotContain("platform:read");
        assertThat(service.tokenVersion(claims)).isEqualTo(3);
        assertThat(claims.getAudience()).containsExactly("enterprise-platform-api-test");
    }
}
