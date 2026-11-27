package ru.yandex.practicum.mymarket.model;

import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Попытка оформления заказа (checkout attempt).
 *
 * <p>Одна активная попытка на пользователя — инвариант, защищающий от двойного
 * списания при параллельных нажатиях «Купить» в двух вкладках. Активность
 * помечается заполненным {@code active_user_id}; после финализации попытки
 * (оплате или отказе) поле обнуляется и уникальный индекс освобождает
 * место для следующей попытки. При таймауте попытка остаётся активной: повтор
 * той же кнопки продолжает её с тем же attemptId.
 *
 * <p>Все переходы статуса выполняются условным UPDATE
 * {@code WHERE id = :id AND status = :expectedStatus} (оптимистичный CAS),
 * поэтому параллельные потоки не могут продвинуть одну попытку дважды.
 */
@Table("checkout_attempts")
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(of = "id")
public class CheckoutAttempt {

    public enum Status {
        /** Ожидает оплаты. */
        AWAITING_PAYMENT,
        /** Оплата выполнена. */
        PAID,
        /** Недостаточно средств для оплаты. */
        INSUFFICIENT_FUNDS,
        /** Платёжный шлюз был недоступен. */
        PAYMENT_TIMEOUT
    }

    @Id
    private Long id;

    private String attemptId;

    private Long userId;

    private Long activeUserId;

    private String status;

    private Long orderId;

    private Long paymentAmount;

    private Long paymentBalance;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public CheckoutAttempt(String attemptId, Long userId) {
        this.attemptId = attemptId;
        this.userId = userId;
        this.activeUserId = userId;
        this.status = Status.AWAITING_PAYMENT.name();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}
