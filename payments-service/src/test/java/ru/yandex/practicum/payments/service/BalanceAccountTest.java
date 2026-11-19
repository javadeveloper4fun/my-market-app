package ru.yandex.practicum.payments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import ru.yandex.practicum.payments.config.PaymentsProperties;

/**
 * Тесты атомарного счёта платежей.
 *
 * <p>Проверяется только поведение счёта без HTTP-слоя: этого достаточно, чтобы
 * зафиксировать правила списания и начальный баланс.
 */
class BalanceAccountTest {

    @Test
    void returnsInitialBalance() {
        BalanceAccount account = new BalanceAccount(new PaymentsProperties(10_000L));

        assertThat(account.getBalance()).isEqualTo(10_000L);
    }

    @Test
    void decreasesBalanceOnSuccessfulPayment() {
        BalanceAccount account = new BalanceAccount(new PaymentsProperties(10_000L));

        assertThat(account.withdraw(1500L)).isEqualTo(8500L);
        assertThat(account.getBalance()).isEqualTo(8500L);
    }

    @Test
    void throwsOnInsufficientFunds() {
        BalanceAccount account = new BalanceAccount(new PaymentsProperties(1000L));

        assertThatThrownBy(() -> account.withdraw(1500L))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("1500")
                .hasMessageContaining("1000");
        assertThat(account.getBalance()).isEqualTo(1000L);
    }

    @Test
    void throwsOnNonPositiveAmount() {
        BalanceAccount account = new BalanceAccount(new PaymentsProperties(1000L));

        assertThatThrownBy(() -> account.withdraw(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.withdraw(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resetRestoresInitialBalance() {
        BalanceAccount account = new BalanceAccount(new PaymentsProperties(10_000L));
        account.withdraw(1500L);

        account.reset();

        assertThat(account.getBalance()).isEqualTo(10_000L);
    }
}
