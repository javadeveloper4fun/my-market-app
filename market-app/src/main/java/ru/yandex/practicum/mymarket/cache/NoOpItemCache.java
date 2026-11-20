package ru.yandex.practicum.mymarket.cache;

import java.util.Optional;
import reactor.core.publisher.Mono;

/**
 * Заглушка кеша для профилей без Redis.
 *
 * <p>Она не хранит данные и всегда сообщает промах, поэтому сервис читает
 * каталог из базы. Так тесты, которым Redis не нужен, не требуют запущенного
 * контейнера; отдельный интеграционный тест включает настоящий кеш.
 */
public class NoOpItemCache implements ItemCache {

    @Override
    public Mono<Optional<CachedItem>> getItem(long id) {
        return Mono.just(Optional.empty());
    }

    @Override
    public Mono<Void> putItem(CachedItem item) {
        return Mono.empty();
    }

    @Override
    public Mono<Optional<CachedItemsPage>> getPage(ItemsPageKey key) {
        return Mono.just(Optional.empty());
    }

    @Override
    public Mono<Void> putPage(ItemsPageKey key, CachedItemsPage page) {
        return Mono.empty();
    }

    @Override
    public Mono<Void> invalidatePages() {
        return Mono.empty();
    }

    @Override
    public Mono<Void> invalidateItem(long id) {
        return Mono.empty();
    }

    @Override
    public Mono<Long> catalogVersion() {
        return Mono.just(0L);
    }
}
