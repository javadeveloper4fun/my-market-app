package ru.yandex.practicum.mymarket.cache;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;

/**
 * Кеш каталога в Redis.
 *
 * <p>Хранится JSON-строка, а не Java-сериализация: формат переживает изменение
 * версии приложения и читается через {@code redis-cli}. Используется
 * {@link ReactiveStringRedisTemplate}, а не блокирующий шаблон: в WebFlux
 * блокирующий вызов занял бы поток event loop.
 *
 * <p>Схема cache-aside: сервис сначала читает кеш, при промахе идёт в базу и
 * кладёт результат обратно. Ошибка Redis не должна ломать витрину, поэтому
 * ошибки чтения и записи кеша логируются и превращаются в промах/пустое
 * завершение.
 */
@Component
@ConditionalOnProperty(name = "market.cache.enabled", havingValue = "true", matchIfMissing = true)
public class RedisItemCache implements ItemCache {

    private static final Logger LOG = LoggerFactory.getLogger(RedisItemCache.class);

    private static final String ITEM_PREFIX = "market:item:";

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ItemCacheProperties properties;

    public RedisItemCache(
            ReactiveStringRedisTemplate redis, ObjectMapper objectMapper, ItemCacheProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Mono<Optional<CachedItem>> getItem(long id) {
        return redis.opsForValue()
                .get(itemKey(id))
                .flatMap(json -> deserialize(json, CachedItem.class))
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .onErrorResume(error -> {
                    LOG.warn("Не удалось прочитать карточку товара {} из Redis: {}", id, error.getMessage());
                    return Mono.just(Optional.empty());
                });
    }

    @Override
    public Mono<Void> putItem(CachedItem item) {
        return serialize(item)
                .flatMap(json -> redis.opsForValue().set(itemKey(item.id()), json, properties.ttl()))
                .then()
                .onErrorResume(error -> {
                    LOG.warn("Не удалось сохранить карточку товара {} в Redis: {}", item.id(), error.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Optional<CachedItemsPage>> getPage(ItemsPageKey key) {
        return redis.opsForValue()
                .get(key.toRedisKey())
                .flatMap(json -> deserialize(json, CachedItemsPage.class))
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .onErrorResume(error -> {
                    LOG.warn("Не удалось прочитать страницу каталога из Redis: {}", error.getMessage());
                    return Mono.just(Optional.empty());
                });
    }

    @Override
    public Mono<Void> putPage(ItemsPageKey key, CachedItemsPage page) {
        return serialize(page)
                .flatMap(json -> redis.opsForValue().set(key.toRedisKey(), json, properties.ttl()))
                .then()
                .onErrorResume(error -> {
                    LOG.warn("Не удалось сохранить страницу каталога в Redis: {}", error.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Void> invalidatePages() {
        return redis.opsForValue().increment(ItemsPageKey.versionKey()).then().onErrorResume(error -> {
            LOG.warn("Не удалось инвалидировать страницы каталога: {}", error.getMessage());
            return Mono.empty();
        });
    }

    @Override
    public Mono<Void> invalidateItem(long id) {
        return redis.delete(itemKey(id)).then().onErrorResume(error -> {
            LOG.warn("Не удалось удалить карточку товара {} из Redis: {}", id, error.getMessage());
            return Mono.empty();
        });
    }

    @Override
    public Mono<Long> catalogVersion() {
        return redis.opsForValue()
                .get(ItemsPageKey.versionKey())
                .map(Long::parseLong)
                .defaultIfEmpty(0L)
                .onErrorReturn(0L);
    }

    /**
     * Сериализует значение в JSON.
     *
     * @param value значение
     * @return JSON-строка
     */
    private Mono<String> serialize(Object value) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(value)).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Десериализует JSON.
     *
     * @param json  JSON-строка
     * @param clazz тип значения
     * @param <T>   тип значения
     * @return значение
     */
    private <T> Mono<T> deserialize(String json, Class<T> clazz) {
        return Mono.fromCallable(() -> objectMapper.readValue(json, clazz)).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Собирает ключ карточки.
     *
     * @param id идентификатор
     * @return ключ Redis
     */
    private String itemKey(long id) {
        return ITEM_PREFIX + id;
    }
}
