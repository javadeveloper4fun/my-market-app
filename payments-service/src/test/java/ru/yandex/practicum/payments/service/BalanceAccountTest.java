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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import ru.yandex.practicum.payments.config.PaymentsProperties;

class BalanceAccountTest {

    @Test
    void returnsInitialBalancePerUser() {
        BalanceAccount account = new BalanceAccount(new PaymentsProperties(10_000L));

        assertThat(account.getBalance(1L)).isEqualTo(10_000L);
        assertThat(account.getBalance(2L)).isEqualTo(10_000L);
    }

    @Test
    void decreasesBalanceOnSuccessfulPayment() {
        BalanceAccount account = new BalanceAccount(new PaymentsProperties(10_000L));

        assertThat(account.withdraw(1L, 1500L)).isEqualTo(8500L);
        assertThat(account.getBalance(1L)).isEqualTo(8500L);
        assertThat(account.getBalance(2L)).isEqualTo(10_000L);
    }

    @Test
    void withdrawsConcurrentlyWithoutOverdrawing() throws Exception {
        int threads = 32;
        long amount = 1_000L;
        BalanceAccount account = new BalanceAccount(new PaymentsProperties(10_000L));
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Потоки не стартовали одновременно");
                    }
                    try {
                        account.withdraw(1L, amount);
                        successes.incrementAndGet();
                    } catch (InsufficientFundsException expected) {
                        failures.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(successes.get()).isEqualTo(10);
        assertThat(failures.get()).isEqualTo(22);
        assertThat(successes.get() + failures.get()).isEqualTo(threads);
        assertThat(account.getBalance(1L)).isZero();
    }

    @Test
    void throwsOnInsufficientFunds() {
        BalanceAccount account = new BalanceAccount(new PaymentsProperties(1000L));

        assertThatThrownBy(() -> account.withdraw(1L, 1500L))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("1500")
                .hasMessageContaining("1000");
        assertThat(account.getBalance(1L)).isEqualTo(1000L);
    }

    @Test
    void throwsOnNonPositiveAmount() {
        BalanceAccount account = new BalanceAccount(new PaymentsProperties(1000L));

        assertThatThrownBy(() -> account.withdraw(1L, 0)).isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> account.withdraw(1L, -1)).isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void resetRestoresInitialBalance() {
        BalanceAccount account = new BalanceAccount(new PaymentsProperties(10_000L));
        account.withdraw(1L, 1500L);

        account.reset();

        assertThat(account.getBalance(1L)).isEqualTo(10_000L);
    }
}
