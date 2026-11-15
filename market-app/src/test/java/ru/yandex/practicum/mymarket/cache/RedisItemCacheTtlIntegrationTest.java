package ru.yandex.practicum.mymarket.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.repository.ItemRepository;
import ru.yandex.practicum.mymarket.service.ItemService;
import ru.yandex.practicum.mymarket.support.DatabaseResetTest;

/**
 * Проверяет истечение срока жизни записей кеша каталога.
 *
 * <p>Тест использует короткий TTL, чтобы не ждать минуты. Контейнер Redis
 * поднимается Testcontainers и пропускается, если Docker недоступен.
 */
@SpringBootTest(properties = {"market.cache.enabled=true", "market.cache.items.ttl=PT1S"})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class RedisItemCacheTtlIntegrationTest extends DatabaseResetTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private ItemService itemService;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @BeforeEach
    void flushRedis() {
        try (ReactiveRedisConnection connection =
                redisTemplate.getConnectionFactory().getReactiveConnection()) {
            connection.serverCommands().flushAll().block();
        }
    }

    @Test
    void cachedItemExpiresAfterTtl() throws Exception {
        Item saved = itemRepository
                .save(new Item("Куртка", "Описание", "images/test.jpg", new BigDecimal("100")))
                .block();
        assertThat(saved).isNotNull();

        itemService.getItem(saved.getId()).block();

        String key = "market:item:" + saved.getId();
        assertThat(redisTemplate.hasKey(key).block()).isTrue();

        waitUntilExpired(key, Duration.ofSeconds(5));

        assertThat(redisTemplate.hasKey(key).block()).isFalse();
    }

    @Test
    void cachedPageExpiresAfterTtl() throws Exception {
        itemRepository
                .save(new Item("Куртка", "Описание", "images/test.jpg", new BigDecimal("100")))
                .block();

        itemService.getItems("", "NO", 1, 10).block();

        // При чтении страницы RedisItemCache подставляет актуальную версию
        // каталога (0, если ключ версии отсутствует).
        String key = new ItemsPageKey(0, "", "NO", 1, 10).toRedisKey();
        assertThat(redisTemplate.hasKey(key).block()).isTrue();

        waitUntilExpired(key, Duration.ofSeconds(5));

        assertThat(redisTemplate.hasKey(key).block()).isFalse();
    }

    private void waitUntilExpired(String key, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline
                && Boolean.TRUE.equals(redisTemplate.hasKey(key).block())) {
            Thread.sleep(100);
        }
    }
}
