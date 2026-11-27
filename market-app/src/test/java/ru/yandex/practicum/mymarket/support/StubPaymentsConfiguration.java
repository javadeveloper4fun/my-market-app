package ru.yandex.practicum.mymarket.support;

import java.time.Duration;
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
        return new StubPaymentsGateway();
    }

    public static final class StubPaymentsGateway implements PaymentsGateway {
        private final ConcurrentMap<Long, AtomicLong> balances = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, PaymentResult> results = new ConcurrentHashMap<>();
        private volatile long initialBalanceForAll = 1_000_000L;
        private volatile boolean unavailable;
        private volatile Duration paymentDelay = Duration.ZERO;

        public void setBalance(long value) {
            initialBalanceForAll = value;
            balances.clear();
            results.clear();
            balances.put(0L, new AtomicLong(value));
        }

        public void setUnavailable(boolean value) {
            unavailable = value;
        }

        public void setPaymentDelay(Duration value) {
            paymentDelay = value;
        }

        @Override
        public Mono<Long> getBalance(long userId) {
            if (unavailable) {
                return Mono.error(new PaymentsUnavailableException("stub unavailable"));
            }
            return Mono.fromSupplier(() -> balanceFor(userId).get());
        }

        @Override
        public Mono<PaymentResult> pay(long userId, long amount, String attemptId) {
            if (unavailable) {
                return Mono.error(new PaymentsUnavailableException("stub unavailable"));
            }
            Mono<PaymentResult> paymentResult = Mono.fromCallable(() -> {
                // Идемпотентность: тот же attemptId возвращает сохранённый результат
                PaymentResult existing = results.get(attemptId);
                if (existing != null) {
                    return existing;
                }
                // Атомарное списание через CAS (аналог BalanceAccount.withdraw)
                AtomicLong balance = balanceFor(userId);
                long remaining;
                while (true) {
                    long current = balance.get();
                    if (current < amount) {
                        throw new InsufficientFundsException();
                    }
                    remaining = current - amount;
                    if (balance.compareAndSet(current, remaining)) {
                        break;
                    }
                }
                PaymentResult result = new PaymentResult(amount, remaining);
                // Сохраняем результат только если никто другой не записал ранее (race-safe)
                PaymentResult previous = results.putIfAbsent(attemptId, result);
                if (previous != null) {
                    // Другой поток уже записал результат — отменяем наше списание и возвращаем его
                    balance.addAndGet(amount);
                    return previous;
                }
                return result;
            });
            return paymentDelay.isZero() ? paymentResult : paymentResult.delaySubscription(paymentDelay);
        }

        private AtomicLong balanceFor(long userId) {
            return balances.computeIfAbsent(userId, ignored -> new AtomicLong(initialBalanceForAll));
        }

        public void setBalanceForAll(long value) {
            initialBalanceForAll = value;
            balances.clear();
            results.clear();
            balances.put(0L, new AtomicLong(value));
        }
    }
}
