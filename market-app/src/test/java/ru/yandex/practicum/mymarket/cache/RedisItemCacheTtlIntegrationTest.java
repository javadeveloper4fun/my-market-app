package ru.yandex.practicum.mymarket.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.repository.ItemRepository;
import ru.yandex.practicum.mymarket.service.ItemService;
import ru.yandex.practicum.mymarket.support.AbstractRedisIntegrationTest;

/**
 * Проверяет истечение срока жизни записей кеша каталога.
 *
 * <p>Тест использует короткий TTL, чтобы не ждать минуты. Контейнер Redis и
 * Spring-контекст общие с {@link RedisItemCacheIntegrationTest}: оба класса
 * наследуют {@link AbstractRedisIntegrationTest}, поэтому Testcontainers
 * поднимает один Redis, а Spring переиспользует один контекст.
 */
class RedisItemCacheTtlIntegrationTest extends AbstractRedisIntegrationTest {

    @Autowired
    private ItemService itemService;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private ItemCache itemCache;

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

        // Страница кешируется под версией каталога, прочитанной при запросе;
        // берём её из кеша, а не литерал 0.
        long version = itemCache.catalogVersion().block();
        String key = new ItemsPageKey(version, "", "NO", 1, 10).toRedisKey();
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
