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
import ru.yandex.practicum.mymarket.model.Order;
import ru.yandex.practicum.mymarket.model.OrderItem;
import ru.yandex.practicum.mymarket.support.DatabaseResetTest;

/**
 * Тесты репозитория заказов на встроенной H2 (профиль test).
 *
 * <p>Изоляция тестов и подключение миграций — по тем же причинам, что и в
 * {@link ItemRepositoryTest}; подробности там.
 *
 * <p>Проверок, которыми был наполнен этот класс в блокирующей версии, здесь
 * нет, и это следствие перехода на R2DBC, а не упрощение тестов.
 *
 * <p>Исчезли три проверки, и каждая опиралась на возможность JPA, которой в
 * R2DBC нет. Первая — каскадное сохранение позиций вместе с заказом через
 * {@code @MappedCollection}: конвертер R2DBC не пишет вложенные сущности и
 * отвечает {@code Nested entities are not supported}. Вторая — загрузка заказа
 * вместе с позициями одним запросом через {@code left join fetch}: такого
 * способа здесь нет, позиции читаются отдельным запросом. Третья — проверка на
 * дубли заказа, возникшие от размножения строки соединением по числу позиций,
 * и на их подавление через {@code distinct}: причина исчезла вместе с
 * соединением, а {@code distinct} в JPQL и в SQL вели себя по-разному и в R2DBC
 * не применяется.
 *
 * <p>На месте этих проверок — то, чем код пользуется теперь: две отдельные
 * записи и два отдельных чтения.
 */
@DataR2dbcTest
@ImportAutoConfiguration(LiquibaseAutoConfiguration.class)
@Import(LiquibaseDataSourceConfig.class)
@ActiveProfiles("test")
class OrderRepositoryTest extends DatabaseResetTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    /**
     * Проверяет сохранение заказа и его позиций отдельными вызовами.
     *
     * <p>Порядок обязателен: идентификатор заказа появляется только после
     * вставки, и до этого проставить его в позициях нечем.
     */
    @Test
    void savesOrderAndItsItemsSeparately() {
        Order order = orderRepository.save(new Order()).block();

        orderItemRepository
                .save(new OrderItem(order.getId(), 1L, "Куртка", new BigDecimal("4500.00"), 2))
                .block();
        orderItemRepository
                .save(new OrderItem(order.getId(), 2L, "Плед", new BigDecimal("1600.00"), 1))
                .block();

        assertThat(order.getId()).isNotNull();
        List<OrderItem> items = orderItemRepository
                .findAllByOrderIdIn(List.of(order.getId()))
                .collectList()
                .block();
        assertThat(items).hasSize(2);
        assertThat(items).extracting(OrderItem::getItemId).containsExactlyInAnyOrder(1L, 2L);
        assertThat(items).allSatisfy(item -> assertThat(item.getOrderId()).isEqualTo(order.getId()));
    }

    /**
     * Проверяет, что позиции запрашиваются по идентификатору заказа.
     */
    @Test
    void findsItemsByOrderId() {
        Order order = orderRepository.save(new Order()).block();
        Order other = orderRepository.save(new Order()).block();
        orderItemRepository
                .save(new OrderItem(order.getId(), 1L, "Куртка", new BigDecimal("4500.00"), 2))
                .block();
        orderItemRepository
                .save(new OrderItem(other.getId(), 9L, "Чужой товар", new BigDecimal("100.00"), 1))
                .block();

        List<OrderItem> items = orderItemRepository
                .findAllByOrderIdIn(List.of(order.getId()))
                .collectList()
                .block();

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().getTitle()).isEqualTo("Куртка");
        assertThat(items.getFirst().getCount()).isEqualTo(2);
    }

    /**
     * Проверяет выборку идентификаторов всех заказов.
     *
     * <p>Заказы возвращаются отсортированными по id убыванию: свежие — первыми.
     */
    @Test
    void findAllIdsReturnsSavedOrders() {
        Order first = orderRepository.save(new Order()).block();
        Order second = orderRepository.save(new Order()).block();

        List<Long> ids = orderRepository.findAllIds().collectList().block();

        assertThat(ids).containsExactly(second.getId(), first.getId());
    }

    /**
     * Проверяет, что заказ без позиций читается как пустой, а не теряется.
     */
    @Test
    void findsOrderWithoutItems() {
        Order order = orderRepository.save(new Order()).block();

        assertThat(orderRepository.findById(order.getId()).block()).isNotNull();
        assertThat(orderItemRepository
                        .findAllByOrderIdIn(List.of(order.getId()))
                        .collectList()
                        .block())
                .isEmpty();
    }
}
