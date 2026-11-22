package ru.yandex.practicum.mymarket.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.r2dbc.test.autoconfigure.DataR2dbcTest;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import ru.yandex.practicum.mymarket.PostgreSqlTestContainerConfiguration;
import ru.yandex.practicum.mymarket.config.LiquibaseDataSourceConfig;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.support.DatabaseResetTest;

/**
 * Проверка слоя доступа к данным на настоящем PostgreSQL (Спринт 5, Тема 5, Урок 2).
 *
 * <p>Основные тесты репозиториев работают на встроенной H2 ради скорости. Здесь тот
 * же код проверяется на боевой СУБД: контейнер поднимает PostgreSQL, Liquibase
 * применяет тот же changelog, что и в {@code docker-compose.yml}. Тест падает,
 * если changelog перестанет соответствовать сущностям или если PostgreSQL ведёт
 * себя иначе, чем H2 в режиме {@code MODE=PostgreSQL}.
 *
 * <p>Миграции и бин {@code DataSource} для них подключаются явно — по тем же
 * причинам, что и в {@link ItemRepositoryTest}. Именно здесь это видно лучше
 * всего: автоконфигурация Liquibase читает параметры из
 * {@code JdbcConnectionDetails}, которые публикует {@code @ServiceConnection}, и
 * без явного бина {@code DataSource} схему не создал бы никто.
 *
 * <p>Требует работающего Docker: без него тест не запускается.
 */
@DataR2dbcTest
@ImportAutoConfiguration(LiquibaseAutoConfiguration.class)
@Import({LiquibaseDataSourceConfig.class, PostgreSqlTestContainerConfiguration.class})
@ActiveProfiles("testcontainers")
class ItemRepositoryPostgreSqlTest extends DatabaseResetTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private DataSource dataSource;

    /**
     * Проверяет, что тесты идут на PostgreSQL, а не на H2.
     *
     * <p>Без этой проверки класс молча потерял бы смысл: срез подставляет
     * встроенную базу, если не нашёл источник подключения, и остальные тесты
     * прошли бы на H2, ничего об этом не сообщив. Имя продукта берётся у драйвера,
     * поэтому сменить СУБД, не меняя остальные настройки, здесь нельзя.
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
     * Возвращает PostgreSQL-вариант сброса счётчиков ({@code ALTER SEQUENCE ... RESTART WITH 1}).
     * Используется вместо H2-варианта, так как PostgreSQL не поддерживает
     * {@code ALTER TABLE ... ALTER COLUMN ... RESTART WITH}.
     *
     * @return список операторов очистки для PostgreSQL
     */
    @Override
    protected List<String> resetStatements() {
        return postgreSqlResetStatements();
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
        Item jacket = save(new Item("Куртка", "Тёплая куртка", "images/jacket.jpg", new BigDecimal("4500.00")));
        save(new Item("Шапка", "Тёплая шапка", "images/boots.jpg", new BigDecimal("999.99")));

        BigDecimal total = itemRepository
                .findAll()
                .map(Item::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .block();

        assertThat(jacket.getPrice()).isEqualByComparingTo("4500.00");
        assertThat(jacket.getPrice().scale()).isEqualTo(2);
        assertThat(total).isEqualByComparingTo("5499.99");
        assertThat(total.scale()).isEqualTo(2);
    }

    /**
     * Проверяет поиск по названию без учёта регистра на реальной СУБД.
     *
     * <p>Запрос использует {@code lower(...)} с обеих сторон, поэтому регистр не
     * должен влиять на результат.
     */
    @Test
    void searchesIgnoringCase() {
        save(new Item("Куртка", "Тёплая куртка", "images/jacket.jpg", new BigDecimal("4500.00")));
        save(new Item("Рюкзак", "Стальной рюкзак", "images/backpack.jpg", new BigDecimal("3200.00")));

        List<Item> found =
                itemRepository.searchUnsorted("кУрТкА", 10, 0).collectList().block();

        assertThat(found).hasSize(1);
        assertThat(found.getFirst().getTitle()).isEqualTo("Куртка");
    }

    /**
     * Проверяет, что позиции корзины возвращаются только для запрошенных
     * идентификаторов.
     *
     * <p>Выборка ограничена списком идентификаторов. Если ограничение потерялось,
     * вернулась бы позиция лишнего товара, и витрина показала бы ненулевой счётчик
     * у товара, которого в корзине нет.
     */
    @Test
    void countsCartItemsOnlyForRequestedIds() {
        Item jacket = save(new Item("Куртка", "Тёплая куртка", "images/jacket.jpg", new BigDecimal("4500.00")));
        Item hat = save(new Item("Шапка", "Тёплая шапка", "images/boots.jpg", new BigDecimal("999.99")));
        cartItemRepository.save(new CartItem(jacket.getId(), 3, USER_ID)).block();

        List<CartItem> positions = cartItemRepository
                .findAllByItemIdInAndUserId(List.of(hat.getId()), USER_ID)
                .collectList()
                .block();

        assertThat(positions).isEmpty();
    }

    /**
     * Проверяет, что количество возвращается для товара, который действительно в корзине.
     *
     * <p>Дополняет {@link #countsCartItemsOnlyForRequestedIds}: вместе тесты отделяют
     * правильную фильтрацию от пустого результата.
     */
    @Test
    void countsCartItemForRequestedId() {
        Item jacket = save(new Item("Куртка", "Тёплая куртка", "images/jacket.jpg", new BigDecimal("4500.00")));
        cartItemRepository.save(new CartItem(jacket.getId(), 3, USER_ID)).block();

        List<CartItem> positions = cartItemRepository
                .findAllByItemIdInAndUserId(List.of(jacket.getId()), USER_ID)
                .collectList()
                .block();

        assertThat(positions).hasSize(1);
        assertThat(positions.getFirst().getItemId()).isEqualTo(jacket.getId());
        assertThat(positions.getFirst().getCount()).isEqualTo(3);
    }

    /**
     * Проверяет, что уменьшение количества затрагивает только выбранную позицию.
     *
     * <p>Запрос ограничен {@code item_id}; без этого условия уменьшились бы все
     * позиции корзины. Возвращённое число строк подтверждает, что обновление
     * затронуло ровно одну позицию.
     */
    @Test
    void decreaseCountUpdatesOnlyRequestedCartItem() {
        Item jacket = save(new Item("Куртка", "Тёплая куртка", "images/jacket.jpg", new BigDecimal("4500.00")));
        Item hat = save(new Item("Шапка", "Тёплая шапка", "images/boots.jpg", new BigDecimal("999.99")));
        cartItemRepository.save(new CartItem(jacket.getId(), 5, USER_ID)).block();
        cartItemRepository.save(new CartItem(hat.getId(), 4, USER_ID)).block();

        assertThat(cartItemRepository.decreaseCount(jacket.getId(), 2, USER_ID).block())
                .isEqualTo(1);

        CartItem jacketPosition = cartItemRepository
                .findByItemIdAndUserId(jacket.getId(), USER_ID)
                .block();
        CartItem hatPosition =
                cartItemRepository.findByItemIdAndUserId(hat.getId(), USER_ID).block();
        assertThat(jacketPosition).isNotNull();
        assertThat(jacketPosition.getCount()).isEqualTo(3);
        assertThat(hatPosition).isNotNull();
        assertThat(hatPosition.getCount()).isEqualTo(4);
    }

    /**
     * Проверяет удаление только неположительных остатков.
     *
     * <p>Сервис оплаты уменьшает количество на оплаченное число и затем удаляет
     * позицию с остатком {@code <= 0}. Здесь одна позиция уходит в минус, а
     * вторая остаётся положительной: удалиться должна только первая.
     */
    @Test
    void deleteIfEmptyRemovesOnlyNonPositivePositions() {
        Item jacket = save(new Item("Куртка", "Тёплая куртка", "images/jacket.jpg", new BigDecimal("4500.00")));
        Item hat = save(new Item("Шапка", "Тёплая шапка", "images/boots.jpg", new BigDecimal("999.99")));
        cartItemRepository.save(new CartItem(jacket.getId(), 1, USER_ID)).block();
        cartItemRepository.save(new CartItem(hat.getId(), 3, USER_ID)).block();

        cartItemRepository.decreaseCount(jacket.getId(), 2, USER_ID).block();
        cartItemRepository.decreaseCount(hat.getId(), 1, USER_ID).block();

        assertThat(cartItemRepository.deleteIfEmpty(jacket.getId(), USER_ID).block())
                .isEqualTo(1);
        assertThat(cartItemRepository.deleteIfEmpty(hat.getId(), USER_ID).block())
                .isZero();

        assertThat(cartItemRepository
                        .findByItemIdAndUserId(jacket.getId(), USER_ID)
                        .block())
                .isNull();
        CartItem hatPosition =
                cartItemRepository.findByItemIdAndUserId(hat.getId(), USER_ID).block();
        assertThat(hatPosition).isNotNull();
        assertThat(hatPosition.getCount()).isEqualTo(2);
    }

    /**
     * Проверяет, что уменьшение отсутствующей позиции не меняет данные.
     *
     * <p>Возвращённое число строк равно нулю: сервис может отличить
     * фактическое обновление от холостого запроса.
     */
    @Test
    void decreaseCountReturnsZeroWhenPositionIsAbsent() {
        Item jacket = save(new Item("Куртка", "Тёплая куртка", "images/jacket.jpg", new BigDecimal("4500.00")));

        assertThat(cartItemRepository.decreaseCount(jacket.getId(), 1, USER_ID).block())
                .isZero();
    }

    /**
     * Создаёт и сохраняет товар.
     *
     * @param item товар для сохранения
     * @return сохранённый товар
     */
    private Item save(Item item) {
        return itemRepository.save(item).block();
    }
}
