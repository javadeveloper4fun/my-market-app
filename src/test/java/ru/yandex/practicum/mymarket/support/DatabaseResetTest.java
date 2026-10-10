package ru.yandex.practicum.mymarket.support;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;

/**
 * Общая очистка базы перед каждым тестом.
 *
 * <p>На блокирующем стеке изоляцию обеспечивал {@code @Transactional} на классе
 * теста: он открывал транзакцию и откатывал её после метода. Здесь откат заменён
 * явной очисткой по двум причинам.
 *
 * <p>Первая: срез {@code @DataR2dbcTest} в Boot 4 не помечен
 * {@code @Transactional}, тогда как {@code @DataJpaTest} предыдущих спринтов
 * был. Разница видна прямо в аннотациях: у второго есть
 * {@code org.springframework.transaction.annotation.Transactional}, у первого
 * нет. По этому признаку инфраструктура тестов и решает, открывать ли
 * транзакцию.
 *
 * <p>Вторая: менеджер транзакций приложения — {@code R2dbcTransactionManager},
 * а он реализует {@code ReactiveTransactionManager}, а не
 * {@code PlatformTransactionManager}. Даже если бы транзакция запрашивалась,
 * инфраструктура тестов не нашла бы подходящего менеджера и упала бы с
 * {@code Failed to retrieve PlatformTransactionManager}.
 *
 * <p>Очистка выполнена отдельными {@code DELETE}, а не одним {@code TRUNCATE} с
 * перечислением таблиц: такой синтаксис понимает PostgreSQL, но не H2, который
 * допускает в {@code TRUNCATE} только одну таблицу. Операторы выполняются
 * последовательно, потому что R2DBC не принимает несколько выражений в одном
 * запросе.
 *
 * <p>Счётчики идентификаторов сбрасываются явно. Пока этого нет, идентификаторы
 * продолжались бы с места, где остановился предыдущий тест, и проверки,
 * ожидающие конкретных значений, падали бы без всякой связи с логикой.
 *
 * <p>Тема 7/11 «Тестирование Spring Data R2DBC», урок 1: там состояние базы
 * задаётся скриптом — встроенная реактивная БД и {@code schema.sql},
 * выполняемый перед каждым прогоном, — поэтому очистка данных тестам не нужна.
 * У нас схему создаёт Liquibase (профиль {@code test}, {@code contexts: test}
 * исключает стартовое наполнение витрины), а наполняют базу сами тесты,
 * поэтому очистка необходима. Требование то же: состояние, в котором
 * начинается тест, не должно зависеть от порядка выполнения.
 *
 * <p>Сама очистка идёт через {@link DatabaseClient} — тот же инструмент, что
 * разбирается в теме 4, урок 3/3. Выполнение операторов последовательное,
 * потому что R2DBC не принимает несколько выражений в одном запросе.
 */
public abstract class DatabaseResetTest {

    /**
     * Операторы очистки в порядке, обратном зависимостям: позиции заказа ссылаются
     * на заказы и товары, поэтому их нужно удалять первыми.
     */
    private static final List<String> RESET_STATEMENTS = List.of(
            "DELETE FROM order_item",
            "DELETE FROM cart_item",
            "DELETE FROM orders",
            "DELETE FROM items",
            "ALTER TABLE order_item ALTER COLUMN id RESTART WITH 1",
            "ALTER TABLE cart_item ALTER COLUMN id RESTART WITH 1",
            "ALTER TABLE orders ALTER COLUMN id RESTART WITH 1",
            "ALTER TABLE items ALTER COLUMN id RESTART WITH 1");

    @Autowired
    protected DatabaseClient databaseClient;

    /**
     * Очищает таблицы и сбрасывает счётчики перед каждым тестом.
     */
    @BeforeEach
    void clearDatabase() {
        for (String statement : RESET_STATEMENTS) {
            databaseClient.sql(statement).then().block();
        }
    }
}
