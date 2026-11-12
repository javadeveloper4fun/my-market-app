package ru.yandex.practicum.mymarket.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * Проверка fail-open кеша: недоступный Redis не должен ломать витрину.
 *
 * <p>Настоящий отказ Redis здесь не нужен: достаточно, чтобы операции
 * {@link ReactiveStringRedisTemplate} возвращали ошибку. Кеш — оптимизация,
 * а не источник истины, поэтому его отказ превращается в промах или пустое
 * завершение записи, а не в ошибку запроса.
 */
@ExtendWith(MockitoExtension.class)
class CacheFailureTest {

    @Mock
    private ReactiveStringRedisTemplate redis;

    @Mock
    private ReactiveValueOperations<String, String> operations;

    private RedisItemCache cache;

    @BeforeEach
    void setUp() {
        cache = new RedisItemCache(redis, new ObjectMapper(), new ItemCacheProperties(Duration.ofMinutes(5)));
        lenient().when(redis.opsForValue()).thenReturn(operations);
    }

    @Test
    void getItemReturnsEmptyOnRedisError() {
        when(operations.get(anyString())).thenReturn(Mono.error(new RuntimeException("Redis down")));

        assertThat(cache.getItem(1L).block()).isEmpty();
    }

    @Test
    void getPageReturnsEmptyOnRedisError() {
        when(operations.get(anyString())).thenReturn(Mono.error(new RuntimeException("Redis down")));

        assertThat(cache.getPage(new ItemsPageKey(0, "_", "NO", 1, 10)).block()).isEmpty();
    }

    @Test
    void putItemCompletesWithoutErrorOnRedisFailure() {
        when(operations.set(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.error(new RuntimeException("Redis down")));

        assertThatCode(() -> cache.putItem(new CachedItem(1L, "Куртка", "", "img.jpg", 1500L))
                        .block())
                .doesNotThrowAnyException();
    }

    @Test
    void invalidatePagesCompletesWithoutErrorOnRedisFailure() {
        when(operations.increment(anyString())).thenReturn(Mono.error(new RuntimeException("Redis down")));

        assertThatCode(() -> cache.invalidatePages().block()).doesNotThrowAnyException();
    }

    @Test
    void invalidateItemCompletesWithoutErrorOnRedisFailure() {
        when(redis.delete(anyString())).thenReturn(Mono.error(new RuntimeException("Redis down")));

        assertThatCode(() -> cache.invalidateItem(1L).block()).doesNotThrowAnyException();
    }
}
