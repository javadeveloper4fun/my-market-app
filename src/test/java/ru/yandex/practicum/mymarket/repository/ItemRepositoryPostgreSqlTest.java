package ru.yandex.practicum.mymarket.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import ru.yandex.practicum.mymarket.PostgreSqlTestContainerConfiguration;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.Item;

/**
 * Проверка слоя доступа к данным на настоящем PostgreSQL (Спринт 5, Тема 5, Урок 2).
 *
 * <p>Основные тесты репозиториев работают на встроенной H2 ради скорости.
 * Здесь тот же код проверяется на боевой СУБД: контейнер поднимает PostgreSQL,
 * Liquibase применяет тот же changelog, что и в {@code docker-compose.yml},
 * а Hibernate сверяет полученную схему с сущностями. Тест падает, если
 * changelog перестанет соответствовать {@code @Entity} или если PostgreSQL
 * ведёт себя иначе, чем H2 в режиме {@code MODE=PostgreSQL}.
 *
 * <p>Требует работающего Docker: без него тест не запускается.
 */
@DataJpaTest
@ActiveProfiles("testcontainers")
@Import(PostgreSqlTestContainerConfiguration.class)
class ItemRepositoryPostgreSqlTest {

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private DataSource dataSource;

    /**
     * Проверяет, что тесты идут на PostgreSQL, а не на H2.
     *
     * <p>Без этой проверки класс молча потерял бы смысл: {@code @DataJpaTest} подставляет
     * встроенную базу, если не нашёл источник подключения, и остальные тесты прошли бы
     * на H2, ничего об этом не сообщив. Имя продукта берётся у драйвера, поэтому сменить
     * СУБД, не меняя остальные настройки, здесь нельзя.
     *
     * @throws SQLException если не удалось получить соединение
     */
    @Test
    void runsOnRealPostgreSqlNotOnH2() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
        }
    }

    /**
     * Проверяет, что цена сохраняется с точностью до копеек.
     *
     * <p>Колонка объявлена как {@code NUMERIC(10,2)}. Для двоичного типа
     * ({@code FLOAT}, {@code DOUBLE PRECISION}) такая сумма дала бы
     * погрешность, и сравнение завершилось бы ошибкой.
     */
    @Test
    void keepsPriceExact() {
        Item jacket = itemRepository.saveAndFlush(
                new Item("Куртка", "Тёплая куртка", "images/jacket.jpg", new BigDecimal("4500.00")));
        itemRepository.saveAndFlush(new Item("Шапка", "Тёплая шапка", "images/boots.jpg", new BigDecimal("999.99")));

        BigDecimal total =
                itemRepository.findAll().stream().map(Item::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(jacket.getPrice()).isEqualByComparingTo("4500.00");
        assertThat(jacket.getPrice().scale()).isEqualTo(2);
        assertThat(total).isEqualByComparingTo("5499.99");
        assertThat(total.scale()).isEqualTo(2);
    }

    /**
     * Проверяет поиск по названию без учёта регистра на реальной СУБД.
     *
     * <p>Запрос из {@code ItemRepository.searchByText} использует
     * {@code lower(...)} с обеих сторон, поэтому регистр не должен влиять
     * на результат.
     */
    @Test
    void searchesIgnoringCase() {
        itemRepository.saveAndFlush(
                new Item("Куртка", "Тёплая куртка", "images/jacket.jpg", new BigDecimal("4500.00")));
        itemRepository.saveAndFlush(
                new Item("Рюкзак", "Стальной рюкзак", "images/backpack.jpg", new BigDecimal("3200.00")));

        var found = itemRepository.searchByText("кУрТкА", org.springframework.data.domain.Pageable.unpaged());

        assertThat(found.getContent()).hasSize(1);
        assertThat(found.getContent().getFirst().getTitle()).isEqualTo("Куртка");
    }

    /**
     * Проверяет, что количество товара в корзине возвращается только для запрошенных
     * идентификаторов.
     *
     * <p>Запрос {@code CartItemRepository.findCountsByItemIds} выбирает две скалярные
     * колонки и ограничен списком идентификаторов. Если ограничение потерялось, выборка
     * вернула бы количество лишнего товара, и витрина показала бы ненулевой счётчик
     * у товара, которого в корзине нет.
     */
    @Test
    void countsCartItemsOnlyForRequestedIds() {
        Item jacket = itemRepository.saveAndFlush(
                new Item("Куртка", "Тёплая куртка", "images/jacket.jpg", new BigDecimal("4500.00")));
        Item hat = itemRepository.saveAndFlush(
                new Item("Шапка", "Тёплая шапка", "images/boots.jpg", new BigDecimal("999.99")));
        cartItemRepository.saveAndFlush(new CartItem(jacket, 3));

        var counts = cartItemRepository.findCountsByItemIds(List.of(hat.getId()));

        assertThat(counts).isEmpty();
    }

    /**
     * Проверяет, что количество возвращается для товара, который действительно в корзине.
     *
     * <p>Дополняет {@link #countsCartItemsOnlyForRequestedIds}: вместе тесты отделяют
     * правильную фильтрацию от пустого результата.
     */
    @Test
    void countsCartItemForRequestedId() {
        Item jacket = itemRepository.saveAndFlush(
                new Item("Куртка", "Тёплая куртка", "images/jacket.jpg", new BigDecimal("4500.00")));
        cartItemRepository.saveAndFlush(new CartItem(jacket, 3));

        var counts = cartItemRepository.findCountsByItemIds(List.of(jacket.getId()));

        assertThat(counts).hasSize(1);
        assertThat(counts.getFirst().getItemId()).isEqualTo(jacket.getId());
        assertThat(counts.getFirst().getItemCount()).isEqualTo(3);
    }
}
