package cn.gov.enterprise.security;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** 用户身份、角色和菜单缓存。Redis不可用时自动回源数据库，避免认证链路失效。 */
@Service
public class PermissionCacheService {
    private static final Logger log = LoggerFactory.getLogger(PermissionCacheService.class);
    private static final String PREFIX = "enterprise:permission:";
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public PermissionCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.security.permission-cache-ttl-seconds:1800}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(Math.max(60, ttlSeconds));
    }

    public SecuritySnapshot identity(Long userId, Supplier<SecuritySnapshot> loader) {
        return getOrLoad(key(userId, "identity"), new TypeReference<SecuritySnapshot>() {}, loader);
    }

    public List<String> roles(Long userId, Supplier<List<String>> loader) {
        return getOrLoad(key(userId, "roles"), new TypeReference<List<String>>() {}, loader);
    }

    public List<SecurityIdentityMapper.SecurityMenuRow> menus(
            Long userId, Supplier<List<SecurityIdentityMapper.SecurityMenuRow>> loader) {
        return getOrLoad(key(userId, "menus"),
                new TypeReference<List<SecurityIdentityMapper.SecurityMenuRow>>() {}, loader);
    }

    public void evict(Long userId) {
        if (userId == null) return;
        try {
            redisTemplate.delete(List.of(key(userId, "identity"), key(userId, "roles"), key(userId, "menus")));
        } catch (RuntimeException exception) {
            log.warn("Permission cache eviction skipped: userId={}, type={}",
                    userId, exception.getClass().getSimpleName());
        }
    }

    private <T> T getOrLoad(String key, TypeReference<T> type, Supplier<T> loader) {
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) return objectMapper.readValue(cached, type);
        } catch (RuntimeException exception) {
            log.warn("Permission cache read failed, using database: key={}, type={}",
                    key, exception.getClass().getSimpleName());
        } catch (java.io.IOException exception) {
            log.warn("Permission cache payload invalid, using database: key={}", key);
        }
        T loaded = loader.get();
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(loaded), ttl);
        } catch (RuntimeException exception) {
            log.warn("Permission cache write skipped: key={}, type={}",
                    key, exception.getClass().getSimpleName());
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            log.warn("Permission cache serialization skipped: key={}", key);
        }
        return loaded;
    }

    private String key(Long userId, String category) {
        return PREFIX + userId + ":" + category;
    }
}
