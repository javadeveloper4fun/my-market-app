package ru.yandex.practicum.mymarket.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.r2dbc.test.autoconfigure.DataR2dbcTest;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import ru.yandex.practicum.mymarket.config.LiquibaseDataSourceConfig;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.support.DatabaseResetTest;

/**
 * Тесты репозитория товаров на встроенной H2 (профиль test).
 *
 * <p>{@code @DataR2dbcTest} вместо {@code @DataJpaTest}: срез поднимает только
 * репозитории и схему, без веб-слоя и сервисов.
 *
 * <p>{@link LiquibaseAutoConfiguration} подключается явно через
 * {@code @ImportAutoConfiguration}, потому что в срез реактивного доступа к
 * данным она не входит. Без неё схему не создаёт никто — ни R2DBC сам по себе,
 * ни автоконфигурация среза, — и первая же вставка падает с «table not found».
 * Раньше это делал {@code @DataJpaTest}, включавший автоконфигурацию Hibernate
 * вместе с миграциями.
 *
 * <p>{@link LiquibaseDataSourceConfig} подключается явно по той же причине:
 * срез исключает обычные {@code @Configuration} через фильтр типов, а без
 * объявленного вручную бина {@code DataSource} автоконфигурация Liquibase
 * не создаёт мигратор.
 *
 * <p>Изоляция тестов обеспечивается не аннотацией, а явной очисткой базы —
 * см. {@link DatabaseResetTest}, где объяснено, почему {@code @Transactional}
 * на тесте на реактивном стеке не откатывает изменения.
 *
 * <p>Методы репозитория возвращают {@code Flux} и {@code Mono}, а не готовые
 * значения, поэтому в тестах они собираются в список или разворачиваются
 * через {@code block()}. Вызов без подписки не выполнит запрос вовсе: ленивый
 * publisher не начинает работу, пока на него не подписались.
 */
@DataR2dbcTest
@ImportAutoConfiguration(LiquibaseAutoConfiguration.class)
@Import(LiquibaseDataSourceConfig.class)
@ActiveProfiles("test")
class ItemRepositoryTest extends DatabaseResetTest {

    @Autowired
    private ItemRepository itemRepository;

    /**
     * Проверяет, что поиск по подстроке названия находит товары
     * независимо от регистра.
     */
    @Test
    void searchFindsBySubstringIgnoringCase() {
        Item jacket = save(testItem("Тестовая куртка", "Тёплая модель.", "1000.00"));
        save(testItem("Тестовый рюкзак", "Городской.", "2000.00"));
        Item shirt = save(testItem("Тестовая футболка", "Из хлопка.", "3000.00"));

        List<Item> result =
                itemRepository.searchUnsorted("тестовая", 10, 0).collectList().block();

        assertThat(result).containsExactlyInAnyOrder(jacket, shirt);
    }

    /**
     * Проверяет, что сортировка по цене применяется к результатам поиска.
     *
     * <p>Сортировка задана отдельным методом репозитория, а не параметром:
     * {@code @Query} в R2DBC не подставляет {@code Pageable}, и {@code ORDER BY}
     * пришлось бы собирать конкатенацией.
     */
    @Test
    void searchSortsByPriceAscending() {
        Item cheapest = save(testItem("Тестовая куртка", "Недорогая.", "1000.00"));
        Item middle = save(testItem("Тестовый рюкзак", "Средняя цена.", "2000.00"));
        Item dearest = save(testItem("Тестовая футболка", "Дорогая.", "3000.00"));

        List<Item> result =
                itemRepository.searchByPrice("Тестов", 10, 0).collectList().block();

        assertThat(result).containsExactly(cheapest, middle, dearest);
    }

    /**
     * Проверяет, что сортировка по названию применяется к результатам поиска.
     */
    @Test
    void searchSortsByTitleAscending() {
        Item aaa = save(testItem("Тестовая арбуз", "Раннее по алфавиту.", "2000.00"));
        Item zzz = save(testItem("Тестовая яблоко", "Позднее по алфавиту.", "1000.00"));

        List<Item> result =
                itemRepository.searchByTitle("Тестовая", 10, 0).collectList().block();

        assertThat(result).containsExactly(aaa, zzz);
    }

    /**
     * Проверяет, что границы выборки действительно применяются: {@code LIMIT} и
     * {@code OFFSET} заданы в тексте запроса, а не подставляются каркасом.
     */
    @Test
    void searchPaginatesResults() {
        Item first = save(testItem("Тестовая куртка", "Первая.", "1000.00"));
        Item second = save(testItem("Тестовый рюкзак", "Вторая.", "2000.00"));
        Item third = save(testItem("Тестовая футболка", "Третья.", "3000.00"));

        List<Item> firstPage =
                itemRepository.searchUnsorted("Тестов", 2, 0).collectList().block();
        List<Item> secondPage =
                itemRepository.searchUnsorted("Тестов", 2, 2).collectList().block();

        assertThat(firstPage).hasSize(2);
        assertThat(firstPage).doesNotContain(third);
        assertThat(secondPage).containsExactly(third);
        assertThat(firstPage).containsExactlyInAnyOrder(first, second);
    }

    /**
     * Проверяет, что количество подходящих товаров считается отдельно от выборки
     * и не зависит от сортировки.
     */
    @Test
    void countByTextCountsMatchesIgnoringSort() {
        save(testItem("Тестовая куртка", "Первая.", "1000.00"));
        save(testItem("Тестовый рюкзак", "Вторая.", "2000.00"));
        save(testItem("Другая вещь", "Третья.", "3000.00"));

        Long total = itemRepository.countByText("Тестов").block();

        assertThat(total).isEqualTo(2L);
    }

    /**
     * Проверяет, что выборка без поиска отдаёт товары витрины.
     */
    @Test
    void findAllReturnsAllItems() {
        Item saved = save(testItem("Тестовая куртка", "Без фильтра.", "1000.00"));

        List<Item> result = itemRepository.findAllUnsorted(10, 0).collectList().block();

        assertThat(result).containsExactly(saved);
    }

    /**
     * Проверяет, что оговорка {@code ESCAPE} в запросе работает с уже
     * экранированным шаблоном: символ {@code %} внутри него остаётся литералом.
     *
     * <p>Строка {@code 100\%} передаётся в репозиторий готовой к поиску. Экранирует
     * её вызывающая сторона — {@code ItemService.escapeLike}, — и её поведение
     * проверяется отдельно, тестом {@code getItemsKeepsRawSearchInModelAndEscapesForRepository}.
     * Здесь проверяется ровно то, за что отвечает репозиторий: понимает ли он
     * экранированный шаблон и трактует ли {@code %} как обычный символ.
     *
     * <p>Проверка нужна и сама по себе. Ошибка с {@code ESCAPE} молчаливая: при
     * неверном символе экранирования запрос не падает, а возвращает пустой
     * результат, и поиск по {@code %} просто перестаёт что-либо находить.
     */
    @Test
    void searchTreatsEscapedPercentAsLiteralCharacter() {
        save(testItem("Товар 100%", "Со знаком процента.", "1000.00"));
        save(testItem("Товар 100", "Без знака.", "2000.00"));

        List<Item> result =
                itemRepository.searchUnsorted("100\\%", 10, 0).collectList().block();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getTitle()).isEqualTo("Товар 100%");
    }

    /**
     * Сохраняет товар и дожидается результата: {@code save()} возвращает
     * {@code Mono}, а без подписки запрос не уйдёт в базу и товар не получит
     * идентификатор.
     *
     * @param item товар для сохранения
     * @return сохранённый товар с проставленным идентификатором
     */
    private Item save(Item item) {
        return itemRepository.save(item).block();
    }

    /**
     * Вспомогательный метод: создаёт товар, который узнаётся по названию,
     * чтобы отличать собственные товары теста от прочих строк таблицы.
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
