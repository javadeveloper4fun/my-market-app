package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.model.Item;

/**
 * Хранилище товаров витрины.
 *
 * <p>Запросы написаны на SQL, а не на JPQL: R2DBC работает напрямую с БД и
 * ничего не переводит.
 *
 * <p>Отдельного метода с {@code Pageable} здесь нет намеренно.
 * {@code ReactiveSortingRepository} умеет только {@code findAll(Sort)}, а
 * {@code @Query} страницы не подставляет: {@code LIMIT} и {@code OFFSET}
 * пришлось бы указывать в тексте. Поэтому каждая выборка принимает границы
 * числами, и шесть вариантов — два состояния поиска на три сортировки —
 * перечислены явно.
 *
 * <p>Сортировка задана фиксированными методами, а не параметром: иначе порядок
 * пришлось бы собирать конкатенацией строки, и в запрос попал бы текст,
 * присланный пользователем.
 *
 * <p>Символ экранирования записан как {@code ESCAPE '\\\\'} — с двумя
 * обратными косыми. Это не опечатка и не избыточность. Запросы написаны
 * текстовыми блоками, а в них обратная косая остаётся управляющим
 * символом: написанная слитно {@code ESCAPE '\\'} превращается в
 * {@code ESCAPE '''}, где символом экранирования оказывается одинарная
 * кавычка. Ошибка молчаливая: экранирование {@code %} перестаёт работать,
 * запрос возвращает пустой результат вместо ожидаемой находки, и ни
 * одного исключения не возникает.
 *
 * <p>Тема 3/11 «Проекты для создания реактивных приложений», урок 2/3:
 * {@link ReactiveCrudRepository} — это R2DBC, протокол, который вместо
 * блокирующего {@code ResultSet} возвращает {@code Publisher} и отдаёт порции
 * строк по мере запроса. Урок 3/3: выборки возвращают {@link Flux}, потому что
 * строк всегда может оказаться от нуля до бесконечности, а методы с одним
 * товаром — {@link Mono}, то есть ноль или один элемент.
 */
public interface ItemRepository extends ReactiveCrudRepository<Item, Long> {

    /**
     * Все товары без сортировки.
     *
     * @param limit  максимум строк в выборке
     * @param offset сколько строк пропустить
     * @return поток товаров
     */
    @Query("SELECT * FROM items LIMIT :limit OFFSET :offset")
    Flux<Item> findAllUnsorted(@Param("limit") long limit, @Param("offset") long offset);

    /**
     * Все товары по алфавиту.
     *
     * @param limit  максимум строк в выборке
     * @param offset сколько строк пропустить
     * @return поток товаров
     */
    @Query("SELECT * FROM items ORDER BY title LIMIT :limit OFFSET :offset")
    Flux<Item> findAllByTitle(@Param("limit") long limit, @Param("offset") long offset);

    /**
     * Все товары по цене.
     *
     * @param limit  максимум строк в выборке
     * @param offset сколько строк пропустить
     * @return поток товаров
     */
    @Query("SELECT * FROM items ORDER BY price LIMIT :limit OFFSET :offset")
    Flux<Item> findAllByPrice(@Param("limit") long limit, @Param("offset") long offset);

    /**
     * Ищет товары по названию или описанию, сортировка не применяется.
     *
     * @param text   искомая подстрока, не {@code null}
     * @param limit  максимум строк в выборке
     * @param offset сколько строк пропустить
     * @return поток найденных товаров
     */
    @Query("""
            SELECT * FROM items
            WHERE lower(title) LIKE lower(concat('%', :text, '%')) ESCAPE '\\'
               OR lower(description) LIKE lower(concat('%', :text, '%')) ESCAPE '\\'
            LIMIT :limit OFFSET :offset
            """)
    Flux<Item> searchUnsorted(@Param("text") String text, @Param("limit") long limit, @Param("offset") long offset);

    /**
     * Ищет товары по названию или описанию, сортировка по алфавиту.
     *
     * @param text   искомая подстрока, не {@code null}
     * @param limit  максимум строк в выборке
     * @param offset сколько строк пропустить
     * @return поток найденных товаров
     */
    @Query("""
            SELECT * FROM items
            WHERE lower(title) LIKE lower(concat('%', :text, '%')) ESCAPE '\\'
               OR lower(description) LIKE lower(concat('%', :text, '%')) ESCAPE '\\'
            ORDER BY title
            LIMIT :limit OFFSET :offset
            """)
    Flux<Item> searchByTitle(@Param("text") String text, @Param("limit") long limit, @Param("offset") long offset);

    /**
     * Ищет товары по названию или описанию, сортировка по цене.
     *
     * @param text   искомая подстрока, не {@code null}
     * @param limit  максимум строк в выборке
     * @param offset сколько строк пропустить
     * @return поток найденных товаров
     */
    @Query("""
            SELECT * FROM items
            WHERE lower(title) LIKE lower(concat('%', :text, '%')) ESCAPE '\\'
               OR lower(description) LIKE lower(concat('%', :text, '%')) ESCAPE '\\'
            ORDER BY price
            LIMIT :limit OFFSET :offset
            """)
    Flux<Item> searchByPrice(@Param("text") String text, @Param("limit") long limit, @Param("offset") long offset);

    /**
     * Считает товары, подходящие под строку поиска, без учёта сортировки:
     * порядок строк на количество не влияет, поэтому одного запроса достаточно.
     *
     * @param text искомая подстрока, не {@code null}
     * @return количество подходящих товаров
     */
    @Query("""
            SELECT count(*) FROM items
            WHERE lower(title) LIKE lower(concat('%', :text, '%')) ESCAPE '\\'
               OR lower(description) LIKE lower(concat('%', :text, '%')) ESCAPE '\\'
            """)
    Mono<Long> countByText(@Param("text") String text);
}
