package ru.yandex.practicum.mymarket.payments;

import reactor.core.publisher.Mono;

public interface PaymentsGateway {
    Mono<Long> getBalance(long userId);

    Mono<PaymentResult> pay(long userId, long amount, String attemptId);
}
