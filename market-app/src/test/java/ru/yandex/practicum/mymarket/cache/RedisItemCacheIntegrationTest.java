package ru.yandex.practicum.mymarket.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.ItemsPage;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.repository.ItemRepository;
import ru.yandex.practicum.mymarket.service.ItemService;
import ru.yandex.practicum.mymarket.support.AbstractRedisIntegrationTest;

/**
 * Интеграционные тесты кеша на настоящем Redis.
 *
 * <p>Основные тесты приложения выключают кеш, чтобы не требовать Redis. Здесь
 * проверяется именно работа с ним: попадание, версия страниц и инвалидация
 * карточки. Контейнер Redis и Spring-контекст общие с
 * {@link RedisItemCacheTtlIntegrationTest}: оба класса наследуют
 * {@link AbstractRedisIntegrationTest}, поэтому Testcontainers поднимает один
 * Redis, а Spring переиспользует один контекст.
 */
class RedisItemCacheIntegrationTest extends AbstractRedisIntegrationTest {

    @Autowired
    private ItemService itemService;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private ItemCache itemCache;

    @Test
    void pageIsServedFromCacheUntilVersionChanges() {
        itemRepository.save(item("Первый", "100")).block();
        assertThat(flatItems(itemService.getItems("", "NO", 1, 10, null).block()))
                .hasSize(1);

        // Прямая запись в БД не инвалидирует кеш: страница должна остаться старой.
        itemRepository.save(item("Второй", "200")).block();
        assertThat(flatItems(itemService.getItems("", "NO", 1, 10, null).block()))
                .hasSize(1);

        // saveWithImage инвалидирует версию страниц, поэтому следующий запрос
        // читает уже три товара, а не закешированные один.
        itemService.saveWithImage("Третий", "Описание", 300, "new.jpg").block();
        assertThat(flatItems(itemService.getItems("", "NO", 1, 10, null).block()))
                .hasSize(3);
    }

    @Test
    void cardIsCachedAndCanBeInvalidated() {
        Item saved = itemRepository.save(item("Старое имя", "100")).block();
        assertThat(itemService.getItem(saved.getId(), null).block().title()).isEqualTo("Старое имя");

        Item changed = itemRepository.findById(saved.getId()).block();
        changed.setTitle("Новое имя");
        itemRepository.save(changed).block();

        // Карточка ещё в кеше: изменение в БД не видно до инвалидации.
        assertThat(itemService.getItem(saved.getId(), null).block().title()).isEqualTo("Старое имя");

        itemCache.invalidateItem(saved.getId()).block();

        assertThat(itemService.getItem(saved.getId(), null).block().title()).isEqualTo("Новое имя");
    }

    private List<ItemDto> flatItems(ItemsPage page) {
        // Строки витрины дополняются заглушками до трёх ячеек, поэтому считаем
        // только настоящие товары: иначе один товар выглядел бы как три.
        return page.rows().stream()
                .flatMap(List::stream)
                .filter(item -> item.id() != ItemDto.PLACEHOLDER_ID)
                .toList();
    }

    private Item item(String title, String price) {
        return new Item(title, "Описание", "images/test.jpg", new BigDecimal(price));
    }
}
