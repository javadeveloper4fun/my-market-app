package ru.yandex.practicum.mymarket.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Заказ покупателя.
 *
 * <p>Строка таблицы {@code orders} с идентификатором владельца ({@code user_id}).
 * Имя таблицы указано явно: по умолчанию из класса {@code Order} получилось бы
 * {@code order}, а в схеме — {@code orders}.
 *
 * <p>Позиции здесь намеренно не хранятся. Первой версией была связь
 * {@code @MappedCollection}, и она выглядела естественно — такой приём
 * сохраняет агрегат целиком в Spring Data JDBC. В R2DBC так не работает:
 * конвертер отвечает {@code Nested entities are not supported} и отказывается
 * писать вложенные сущности. Тема 5/11 «Концепция сущностей для работы
 * с данными», урок 5/7: за маппинг отвечает {@code MappingR2dbcConverter} — тот
 * же конвертер, что переводит значения колонок в типы полей, и он же
 * отказывается работать с вложенными сущностями. Поэтому заказ и его позиции
 * сохраняются отдельными вызовами, а связь задаётся полем {@code orderId} в
 * {@link OrderItem}, которое проставляет {@code OrderService}; заказ создаётся
 * до оплаты в статусе {@code AWAITING_PAYMENT}, а после списания переводится
 * в {@code PAID}. Оркеструет шаги {@code CheckoutService}.
 *
 * <p>Такой сценарий неудобен: связь держится на дисциплине вызывающего кода, а
 * не проверяется преобразователем. Но альтернативы у R2DBC нет, и честнее
 * отметить это в коде, чем оставить конструкцию, которая выглядит рабочей и
 * падает на первой же записи.
 *
 * <p>Статус заказа отражает этап оплаты: {@code AWAITING_PAYMENT} до ответа
 * платежей, {@code PAID} после успешного списания, {@code INSUFFICIENT_FUNDS}
 * при отказе и {@code PAYMENT_TIMEOUT}, если ответ о списании не получен.
 * Повтор с тем же идентификатором попытки может перевести заказ из
 * {@code PAYMENT_TIMEOUT} в {@code PAID} без повторного списания. Legacy-заказы,
 * созданные до введения статусов, считаются оплаченными ({@code PAID} по умолчанию).
 */
@Table("orders")
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(of = "id")
public class Order {

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

    private Long userId;

    private String status;

    private String attemptId;

    public Order(Long userId) {
        this.userId = userId;
        this.status = Status.PAID.name();
    }

    public Order(Long userId, String status, String attemptId) {
        this.userId = userId;
        this.status = status;
        this.attemptId = attemptId;
    }
}
