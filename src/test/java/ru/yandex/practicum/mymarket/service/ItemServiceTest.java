package ru.yandex.practicum.mymarket.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.ItemsPage;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ItemServiceTest {

    @Autowired
    private ItemService itemService;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Test
    void getItemsReturnsRowsOfThreeWithSearchAndSort() {
        Item item1 = itemRepository.save(
                new Item("Тестовая куртка", "Зимняя", "img1.jpg", new java.math.BigDecimal("1000")));
        Item item2 = itemRepository.save(
                new Item("Тестовый рюкзак", "Городской", "img2.jpg", new java.math.BigDecimal("2000")));
        Item item3 = itemRepository.save(
                new Item("Тестовая футболка", "Летняя", "img3.jpg", new java.math.BigDecimal("3000")));

        ItemsPage page = itemService.getItems("Тестов", "ALPHA", 1, 10);

        assertThat(page.rows()).hasSize(1);
        List<ItemDto> row = page.rows().get(0);
        assertThat(row)
                .extracting(ItemDto::title)
                .containsExactly("Тестовая куртка", "Тестовая футболка", "Тестовый рюкзак");
        assertThat(page.search()).isEqualTo("Тестов");
        assertThat(page.sort()).isEqualTo("ALPHA");
        assertThat(page.paging().pageNumber()).isEqualTo(1);
        assertThat(page.paging().pageSize()).isEqualTo(10);
    }

    @Test
    void getItemsPaginatesCorrectly() {
        itemRepository.save(new Item("Тестовая куртка", "1", "img1.jpg", new java.math.BigDecimal("1000")));
        itemRepository.save(new Item("Тестовый рюкзак", "2", "img2.jpg", new java.math.BigDecimal("2000")));
        itemRepository.save(new Item("Тестовая футболка", "3", "img3.jpg", new java.math.BigDecimal("3000")));

        ItemsPage firstPage = itemService.getItems("Тестов", "NO", 1, 2);
        assertThat(firstPage.rows()).hasSize(1);
        assertThat(firstPage.rows().get(0)).hasSize(3);
        assertThat(firstPage.paging().hasNext()).isTrue();

        ItemsPage secondPage = itemService.getItems("Тестов", "NO", 2, 2);
        assertThat(secondPage.paging().hasNext()).isFalse();
    }

    @Test
    void getItemsKeepsRawSearchInModelAndEscapesForRepository() {
        itemRepository.save(new Item("100% хлопок", "Куртка", "img1.jpg", new java.math.BigDecimal("1000")));
        itemRepository.save(new Item("Куртка", "Описание", "img2.jpg", new java.math.BigDecimal("2000")));

        ItemsPage page = itemService.getItems("100%", "NO", 1, 10);

        assertThat(page.search()).isEqualTo("100%");
        assertThat(page.rows()).extracting(row -> row.get(0).title()).containsExactly("100% хлопок");
    }

    @Test
    void getItemsExposesEmptyStringInViewWhenSearchParamIsAbsentOrBlank() {
        itemRepository.save(new Item("Тестовая куртка", "1", "img1.jpg", new java.math.BigDecimal("1000")));

        ItemsPage blankPage = itemService.getItems("", "NO", 1, 10);
        assertThat(blankPage.search()).isEmpty();
        assertThat(blankPage.rows()).hasSize(1);

        ItemsPage absentPage = itemService.getItems(null, "NO", 1, 10);
        assertThat(absentPage.search()).isEmpty();
        assertThat(absentPage.rows()).hasSize(1);
    }

    @Test
    void getItemsTreatsZeroOrNegativePageNumberAsFirstPage() {
        itemRepository.save(new Item("Тестовая куртка", "1", "img1.jpg", new java.math.BigDecimal("1000")));

        ItemsPage page = itemService.getItems("Тестовая", "NO", 0, 2);

        assertThat(page.paging().pageNumber()).isEqualTo(1);
        assertThat(page.paging().hasPrevious()).isFalse();
        assertThat(page.rows()).hasSize(1);
    }

    /**
     * Проверяет, что поиск отбрасывает несовпадающие товары.
     *
     * <p>Остальные тесты используют термин, совпадающий со всеми сохранёнными товарами,
     * поэтому проходят и при полностью отключённом поиске: страница возвращает ту же
     * выборку. Здесь термин подходит только одному товару из трёх, а проверяется всё
     * содержимое строки, а не её первый элемент.
     *
     * <p>Термин взят из описания, а не из названия, чтобы совпадение не зависело от
     * товаров, которые тест создаёт: проверяется, что отфильтрован именно набор
     * результатов поиска, а не наличие слова в конкретном заголовке.
     */
    @Test
    void getItemsExcludesNonMatchingItemsFromRow() {
        itemRepository.save(new Item("Тестовая куртка", "Зимняя", "img1.jpg", new java.math.BigDecimal("1000")));
        itemRepository.save(
                new Item("Тестовый рюкзак", "Городской рюкзак", "img2.jpg", new java.math.BigDecimal("2000")));
        itemRepository.save(new Item("Тестовая футболка", "Летняя", "img3.jpg", new java.math.BigDecimal("3000")));

        ItemsPage page = itemService.getItems("Городской", "NO", 1, 10);

        assertThat(page.rows()).hasSize(1);
        // Строка дополняется пустыми заглушками до трёх элементов, поэтому считаем
        // только реальные товары: заглушки отфильтровывает идентификатор -1.
        assertThat(page.rows().get(0)).filteredOn(dto -> dto.id() != -1).hasSize(1);
        assertThat(page.rows().get(0).getFirst().title()).isEqualTo("Тестовый рюкзак");
    }

    @Test
    void getItemReturnsCountFromCart() {
        Item item = itemRepository.save(
                new Item("Тестовый товар", "Описание", "img.jpg", new java.math.BigDecimal("1500")));
        cartItemRepository.save(new CartItem(item, 3));

        ItemDto dto = itemService.getItem(item.getId());

        assertThat(dto.id()).isEqualTo(item.getId());
        assertThat(dto.count()).isEqualTo(3);
        assertThat(dto.price()).isEqualTo(1500L);
    }
}
