package ru.yandex.practicum.mymarket.support;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.payments.InsufficientFundsException;
import ru.yandex.practicum.mymarket.payments.PaymentResult;
import ru.yandex.practicum.mymarket.payments.PaymentsGateway;
import ru.yandex.practicum.mymarket.payments.PaymentsUnavailableException;

@TestConfiguration(proxyBeanMethods = false)
public class StubPaymentsConfiguration {

    @Bean
    @Primary
    public PaymentsGateway stubPaymentsGateway() {
        return new StubPaymentsGateway(1_000_000L);
    }

    public static final class StubPaymentsGateway implements PaymentsGateway {
        private final ConcurrentMap<Long, AtomicLong> balances = new ConcurrentHashMap<>();
        private final long initialBalance;
        private volatile boolean unavailable;

        StubPaymentsGateway(long initialBalance) {
            this.initialBalance = initialBalance;
        }

        public void setBalance(long value) {
            balances.clear();
            balances.put(0L, new AtomicLong(value));
            this.initialBalanceForAll = value;
        }

        private volatile long initialBalanceForAll = 1_000_000L;

        public void setUnavailable(boolean value) {
            unavailable = value;
        }

        @Override
        public Mono<Long> getBalance(long userId) {
            if (unavailable) {
                return Mono.error(new PaymentsUnavailableException("stub unavailable"));
            }
            return Mono.fromSupplier(() -> balanceFor(userId).get());
        }

        @Override
        public Mono<PaymentResult> pay(long userId, long amount) {
            if (unavailable) {
                return Mono.error(new PaymentsUnavailableException("stub unavailable"));
            }
            return Mono.fromCallable(() -> {
                AtomicLong balance = balanceFor(userId);
                long current = balance.get();
                if (current < amount) {
                    throw new InsufficientFundsException();
                }
                long remaining = current - amount;
                balance.set(remaining);
                return new PaymentResult(amount, remaining);
            });
        }

        private AtomicLong balanceFor(long userId) {
            return balances.computeIfAbsent(userId, ignored -> new AtomicLong(initialBalanceForAll));
        }
    }
}
