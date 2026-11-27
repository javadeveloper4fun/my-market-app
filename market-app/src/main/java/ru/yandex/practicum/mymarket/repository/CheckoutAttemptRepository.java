package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.model.CheckoutAttempt;

/**
 * Хранилище попыток оформления заказа.
 *
 * <p>Уникальный индекс по {@code active_user_id} гарантирует, что у пользователя
 * не более одной активной попытки. Переходы статуса выполняются условным UPDATE
 * {@code WHERE id = :id AND status = :expectedStatus}: если параллельный поток
 * уже изменил состояние, запрос возвращает zero и вызывающий код перечитывает
 * попытку, не повторяя платёж.
 */
public interface CheckoutAttemptRepository extends ReactiveCrudRepository<CheckoutAttempt, Long> {

    /**
     * Находит попытку по идентификатору.
     *
     * @param attemptId идентификатор попытки
     * @return попытка или пустой результат
     */
    Mono<CheckoutAttempt> findByAttemptId(String attemptId);

    /**
     * Находит активную попытку пользователя.
     *
     * @param userId идентификатор пользователя
     * @return активная попытка или пустой результат
     */
    Mono<CheckoutAttempt> findByActiveUserId(Long userId);

    /**
     * Выполняет условный переход статуса с сохранением полезной нагрузки.
     *
     * <p>Передаваемые значения orderId/paymentAmount/paymentBalance не затирают
     * уже сохранённые, если равны null. CAST нужен для NULL-биндов на PostgreSQL.
     *
     * @param id             идентификатор попытки
     * @param newStatus      новый статус
     * @param newActiveId    новое значение active_user_id (null при финализации)
     * @param orderId        идентификатор заказа или null
     * @param paymentAmount  сумма оплаты или null
     * @param paymentBalance баланс после оплаты или null
     * @param expectedStatus ожидаемый текущий статус
     * @return число обновлённых строк
     */
    @Modifying
    @Query("UPDATE checkout_attempts SET status = :newStatus, active_user_id = :newActiveId, "
            + "order_id = COALESCE(CAST(:orderId AS BIGINT), order_id), "
            + "payment_amount = COALESCE(CAST(:paymentAmount AS BIGINT), payment_amount), "
            + "payment_balance = COALESCE(CAST(:paymentBalance AS BIGINT), payment_balance), "
            + "updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = :id AND status = :expectedStatus")
    Mono<Integer> transitionStatus(
            @Param("id") Long id,
            @Param("newStatus") String newStatus,
            @Param("newActiveId") Long newActiveId,
            @Param("orderId") Long orderId,
            @Param("paymentAmount") Long paymentAmount,
            @Param("paymentBalance") Long paymentBalance,
            @Param("expectedStatus") String expectedStatus);

    /**
     * Привязывает заказ к попытке, если попытка ещё не привязана.
     *
     * @param id      идентификатор попытки
     * @param orderId идентификатор заказа
     * @return число обновлённых строк
     */
    @Modifying
    @Query("UPDATE checkout_attempts SET order_id = :orderId, updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = :id AND order_id IS NULL")
    Mono<Integer> attachOrderIfNull(@Param("id") Long id, @Param("orderId") Long orderId);
}
