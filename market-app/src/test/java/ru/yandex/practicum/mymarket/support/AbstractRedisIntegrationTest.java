package ru.yandex.practicum.mymarket.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Общая инфраструктура интеграционных тестов с Redis.
 *
 * <p>Контейнер и Spring-контекст должны быть общими для всех наследников:
 * иначе каждый тестовый класс поднимает свой Redis и создаёт свой контекст,
 * хотя настройки у них одинаковые. {@code @SpringBootTest} и
 * {@code @ActiveProfiles} наследуются, а {@code @DynamicPropertySource}
 * ищется в суперклассах, поэтому ключ кеша контекста совпадает.
 *
 * <p>Сам Redis поднимает {@link RedisTestContainer} один раз на весь JVM:
 * контейнер, управляемый {@code @Container}, останавливался бы после каждого
 * класса, а закешированный контекст продолжал бы смотреть на старый порт.
 */
@SpringBootTest(properties = {"market.cache.enabled=true", "market.cache.items.ttl=PT1S"})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractRedisIntegrationTest extends DatabaseResetTest {

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", RedisTestContainer::host);
        registry.add("spring.data.redis.port", RedisTestContainer::port);
    }

    @Autowired
    protected ReactiveStringRedisTemplate redisTemplate;

    /**
     * Очищает Redis перед каждым тестом, чтобы ключи предыдущего не влияли.
     */
    @BeforeEach
    void flushRedis() {
        try (ReactiveRedisConnection connection =
                redisTemplate.getConnectionFactory().getReactiveConnection()) {
            connection.serverCommands().flushAll().block();
        }
    }
}
