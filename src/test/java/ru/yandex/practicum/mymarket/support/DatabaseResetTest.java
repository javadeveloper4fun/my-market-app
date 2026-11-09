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
 * начинается тест, не зависит от порядка выполнения.
 *
 * <p>Сама очистка идёт через {@link DatabaseClient} — тот же инструмент, что
 * разбирается в теме 4, урок 3/3.
 */
public abstract class DatabaseResetTest {

    /**
     * Возвращает SQL-операторы очистки и сброса счётчиков для H2.
     *
     * @return список операторов очистки
     */
    protected List<String> resetStatements() {
        return List.of(
                "DELETE FROM order_item",
                "DELETE FROM cart_item",
                "DELETE FROM orders",
                "DELETE FROM items",
                "ALTER TABLE order_item ALTER COLUMN id RESTART WITH 1",
                "ALTER TABLE cart_item ALTER COLUMN id RESTART WITH 1",
                "ALTER TABLE orders ALTER COLUMN id RESTART WITH 1",
                "ALTER TABLE items ALTER COLUMN id RESTART WITH 1");
    }

    /**
     * Возвращает SQL-операторы очистки и сброса счётчиков для PostgreSQL.
     * Использует ALTER SEQUENCE, так как PostgreSQL не поддерживает
     * ALTER TABLE ... ALTER COLUMN ... RESTART WITH (это H2-синтаксис);
     * для SERIAL/BIGSERIAL-счётчиков нужен ALTER SEQUENCE ... RESTART WITH.
     *
     * @return список операторов очистки для PostgreSQL
     */
    protected List<String> postgreSqlResetStatements() {
        return List.of(
                "DELETE FROM order_item",
                "DELETE FROM cart_item",
                "DELETE FROM orders",
                "DELETE FROM items",
                "ALTER SEQUENCE order_item_id_seq RESTART WITH 1",
                "ALTER SEQUENCE cart_item_id_seq RESTART WITH 1",
                "ALTER SEQUENCE orders_id_seq RESTART WITH 1",
                "ALTER SEQUENCE items_id_seq RESTART WITH 1");
    }

    @Autowired
    protected DatabaseClient databaseClient;

    /**
     * Очищает таблицы и сбрасывает счётчики перед каждым тестом.
     */
    @BeforeEach
    void clearDatabase() {
        for (String statement : resetStatements()) {
            databaseClient.sql(statement).then().block();
        }
    }
}
