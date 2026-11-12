package ru.yandex.practicum.mymarket.cache;

import java.util.Optional;
import reactor.core.publisher.Mono;

/**
 * Кеш каталога товаров.
 *
 * <p>Интерфейс отделён от Redis, чтобы сервис витрины не зависел от конкретного
 * хранилища, а тесты могли подменить кеш или отключить его. В рабочем профиле
 * реализация работает через Redis, в тестовом по умолчанию используется
 * заглушка без внешнего сервера.
 */
public interface ItemCache {

    /**
     * Читает карточку товара.
     *
     * @param id идентификатор
     * @return карточка или пустой {@link Optional}
     */
    Mono<Optional<CachedItem>> getItem(long id);

    /**
     * Кладёт карточку товара в кеш.
     *
     * @param item карточка
     * @return завершение записи
     */
    Mono<Void> putItem(CachedItem item);

    /**
     * Читает страницу каталога.
     *
     * @param key ключ страницы
     * @return страница или пустой {@link Optional}
     */
    Mono<Optional<CachedItemsPage>> getPage(ItemsPageKey key);

    /**
     * Кладёт страницу каталога в кеш.
     *
     * @param key   ключ страницы
     * @param page  страница
     * @return завершение записи
     */
    Mono<Void> putPage(ItemsPageKey key, CachedItemsPage page);

    /**
     * Инвалидирует все страницы каталога.
     *
     * @return завершение операции
     */
    Mono<Void> invalidatePages();

    /**
     * Удаляет карточку товара из кеша.
     *
     * @param id идентификатор товара
     * @return завершение операции
     */
    Mono<Void> invalidateItem(long id);
}
