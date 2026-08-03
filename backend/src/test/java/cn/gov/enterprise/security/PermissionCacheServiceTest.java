package cn.gov.enterprise.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@ExtendWith(MockitoExtension.class)
class PermissionCacheServiceTest {
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;

    @Test
    void returnsCachedIdentityWithoutCallingDatabaseLoader() throws Exception {
        SecuritySnapshot cached = snapshot(1L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("enterprise:permission:1:identity")).thenReturn(mapper().writeValueAsString(cached));
        AtomicBoolean loaded = new AtomicBoolean(false);

        SecuritySnapshot result = new PermissionCacheService(redisTemplate, mapper(), 1800)
                .identity(1L, () -> { loaded.set(true); return snapshot(1L); });

        assertThat(result).isEqualTo(cached);
        assertThat(loaded).isFalse();
        verify(valueOperations, never()).set(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Duration.class));
    }

    @Test
    void loadsDatabaseAndWritesCacheOnMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("enterprise:permission:2:identity")).thenReturn(null);
        SecuritySnapshot loaded = snapshot(2L);

        SecuritySnapshot result = new PermissionCacheService(redisTemplate, mapper(), 1800)
                .identity(2L, () -> loaded);

        assertThat(result).isSameAs(loaded);
        verify(valueOperations).set(org.mockito.ArgumentMatchers.eq("enterprise:permission:2:identity"),
                org.mockito.ArgumentMatchers.contains("\"userId\":2"),
                org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(1800)));
    }

    private SecuritySnapshot snapshot(Long id) {
        return new SecuritySnapshot(id, "user", 100L, 0, Set.of("profile:view"), Set.of(100L), false, true);
    }

    private ObjectMapper mapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}
