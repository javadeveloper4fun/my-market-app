package ru.yandex.practicum.payments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import ru.yandex.practicum.payments.config.PaymentsProperties;

class PaymentAttemptServiceTest {

    private static final String ATTEMPT_ID = "00000000-0000-0000-0000-000000000001";

    @Test
    void repeatReturnsSameResultWithoutExtraWithdraw() {
        PaymentAttemptService service = new PaymentAttemptService(new BalanceAccount(new PaymentsProperties(10_000L)));

        AttemptRecord first = service.pay(ATTEMPT_ID, 1L, 1500L);
        AttemptRecord second = service.pay(ATTEMPT_ID, 1L, 1500L);

        assertThat(first.status()).isEqualTo(AttemptRecord.Status.PAID);
        assertThat(first.remainingBalance()).isEqualTo(8_500L);
        assertThat(second).isEqualTo(first);
        assertThat(service.accountBalance(1L)).isEqualTo(8_500L);
    }

    @Test
    void repeatAfterInsufficientFundsReturnsSameStatus() {
        PaymentAttemptService service = new PaymentAttemptService(new BalanceAccount(new PaymentsProperties(1000L)));

        AttemptRecord first = service.pay(ATTEMPT_ID, 1L, 1500L);
        AttemptRecord second = service.pay(ATTEMPT_ID, 1L, 1500L);

        assertThat(first.status()).isEqualTo(AttemptRecord.Status.INSUFFICIENT_FUNDS);
        assertThat(second).isEqualTo(first);
        assertThat(service.accountBalance(1L)).isEqualTo(1_000L);
    }

    @Test
    void conflictDifferentUserOrAmountThrows() {
        PaymentAttemptService service = new PaymentAttemptService(new BalanceAccount(new PaymentsProperties(10_000L)));

        service.pay(ATTEMPT_ID, 1L, 1500L);

        assertThatThrownBy(() -> service.pay(ATTEMPT_ID, 2L, 1500L))
                .isInstanceOf(AttemptConflictException.class);
        assertThatThrownBy(() -> service.pay(ATTEMPT_ID, 1L, 2000L))
                .isInstanceOf(AttemptConflictException.class);
    }

    @Test
    void parallelAttemptsSingleWithdraw() throws Exception {
        int threads = 10;
        PaymentAttemptService service = new PaymentAttemptService(new BalanceAccount(new PaymentsProperties(10_000L)));
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch ready = new CountDownLatch(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<AttemptRecord>> futures = new ArrayList<>(threads);
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Потоки не стартовали одновременно");
                    }
                    return service.pay(ATTEMPT_ID, 1L, 1500L);
                }));
            }
            start.countDown();
            for (Future<AttemptRecord> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        // Все потоки должны получить успешный результат (идемпотентный повтор)
        // и только один withdraw должен случиться (баланс 8500, не отрицательный)
        assertThat(futures).hasSize(threads);
        futures.forEach(f -> {
            try {
                assertThat(f.get().status()).isEqualTo(AttemptRecord.Status.PAID);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        assertThat(service.accountBalance(1L)).isEqualTo(8_500L);
    }

    @Test
    void invalidAmountDoesNotConsumeAttemptId() {
        PaymentAttemptService service = new PaymentAttemptService(new BalanceAccount(new PaymentsProperties(10_000L)));

        assertThatThrownBy(() -> service.pay(ATTEMPT_ID, 1L, 0))
                .isInstanceOf(InvalidAmountException.class);
        // Попытка не занята — повтор с той же суммой должен пройти
        AttemptRecord record = service.pay(ATTEMPT_ID, 1L, 1500L);
        assertThat(record.status()).isEqualTo(AttemptRecord.Status.PAID);
        assertThat(record.remainingBalance()).isEqualTo(8_500L);
    }
}
