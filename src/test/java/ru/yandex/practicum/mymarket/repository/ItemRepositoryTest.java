package ru.yandex.practicum.mymarket.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import ru.yandex.practicum.mymarket.model.Item;

/**
 * Тесты репозитория товаров на встроенной H2 (профиль test).
 */
@DataJpaTest
@ActiveProfiles("test")
class ItemRepositoryTest {

    @Autowired
    private ItemRepository itemRepository;

    /**
     * Проверяет, что поиск по подстроке названия находит товары
     * независимо от регистра.
     */
    @Test
    void searchFindsBySubstringIgnoringCase() {
        Item jacket = itemRepository.save(testItem("Тестовая куртка", "Тёплая модель.", "1000.00"));
        itemRepository.save(testItem("Тестовый рюкзак", "Городской.", "2000.00"));
        Item shirt = itemRepository.save(testItem("Тестовая футболка", "Из хлопка.", "3000.00"));

        Page<Item> result = itemRepository.search("тестовая", PageRequest.of(0, 10));

        assertThat(result.getContent()).containsExactlyInAnyOrder(jacket, shirt);
    }

    /**
     * Проверяет, что сортировка по цене применяется к результатам поиска.
     */
    @Test
    void searchSortsByPriceAscending() {
        Item cheapest = itemRepository.save(testItem("Тестовая куртка", "Недорогая.", "1000.00"));
        Item middle = itemRepository.save(testItem("Тестовый рюкзак", "Средняя цена.", "2000.00"));
        Item dearest = itemRepository.save(testItem("Тестовая футболка", "Дорогая.", "3000.00"));

        Page<Item> result = itemRepository.search(
                "Тестов", PageRequest.of(0, 10, Sort.by("price").ascending()));

        assertThat(result.getContent()).containsExactly(cheapest, middle, dearest);
    }

    /**
     * Проверяет, что результат поиска разбивается на страницы заданного размера.
     */
    @Test
    void searchPaginatesResults() {
        itemRepository.save(testItem("Тестовая куртка", "Первая.", "1000.00"));
        itemRepository.save(testItem("Тестовый рюкзак", "Вторая.", "2000.00"));
        itemRepository.save(testItem("Тестовая футболка", "Третья.", "3000.00"));

        Page<Item> firstPage = itemRepository.search("Тестов", PageRequest.of(0, 2));
        Page<Item> secondPage = itemRepository.search("Тестов", PageRequest.of(1, 2));

        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(secondPage.getContent()).hasSize(1);
        assertThat(secondPage.hasNext()).isFalse();
    }

    /**
     * Проверяет, что поиск без текста возвращает все товары витрины.
     */
    @Test
    void searchWithoutTextReturnsAllItems() {
        Item saved = itemRepository.save(testItem("Тестовая куртка", "Без фильтра.", "1000.00"));

        Page<Item> result = itemRepository.search(null, PageRequest.of(0, 10));

        assertThat(result.getContent()).contains(saved);
    }

    /**
     * Вспомогательный метод: создаёт товар с маской «Тестовая» в названии,
     * чтобы в тестах отличать собственных товары от товаров из data.sql.
     *
     * @param title       название
     * @param description описание
     * @param price       цена
     * @return новый товар
     */
    private Item testItem(String title, String description, String price) {
        return new Item(title, description, "images/test.jpg", new BigDecimal(price));
    }
}
