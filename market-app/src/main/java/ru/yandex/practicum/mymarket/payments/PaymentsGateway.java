package ru.yandex.practicum.mymarket.payments;

import reactor.core.publisher.Mono;

/**
 * Шлюз к сервису платежей.
 *
 * <p>Интерфейс отделяет бизнес-логику оформления заказа от сгенерированного
 * OpenAPI-клиента. В тестах его можно заменить заглушкой, не поднимая HTTP-сервер.
 */
public interface PaymentsGateway {

    /**
     * Читает текущий баланс.
     *
     * @return баланс в целых рублях
     */
    Mono<Long> getBalance();

    /**
     * Списывает сумму заказа.
     *
     * @param amount сумма в целых рублях
     * @return результат списания
     */
    Mono<PaymentResult> pay(long amount);
}
