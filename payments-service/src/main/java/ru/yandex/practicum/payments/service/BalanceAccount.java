package ru.yandex.practicum.payments.service;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.payments.config.PaymentsProperties;

/**
 * Баланс счёта в памяти.
 *
 * <p>Списание выполняется через CAS-цикл {@link AtomicLong#compareAndSet}: два
 * одновременных платежа не могут оба увидеть достаточный баланс и увести его
 * в минус. Блокировка здесь не нужна — операция короткая и не содержит
 * ввода-вывода.
 */
@Service
public class BalanceAccount {

    private final AtomicLong balance;
    private final long initialBalance;

    public BalanceAccount(PaymentsProperties properties) {
        if (properties.initialBalance() < 0) {
            throw new IllegalArgumentException("Начальный баланс не может быть отрицательным");
        }
        this.initialBalance = properties.initialBalance();
        this.balance = new AtomicLong(initialBalance);
    }

    /**
     * Возвращает баланс к начальному значению.
     *
     * <p>Нужно только тестам: сервис не хранит историю, и без сброса тесты
     * зависели бы от порядка выполнения. Публичного HTTP-эндпоинта для
     * сброса нет — контракт задания описывает только баланс и платёж.
     */
    public void reset() {
        balance.set(initialBalance);
    }

    /**
     * Возвращает текущий баланс.
     *
     * @return баланс в целых рублях
     */
    public long getBalance() {
        return balance.get();
    }

    /**
     * Списывает сумму, если средств достаточно.
     *
     * @param amount сумма в целых рублях, должна быть положительной
     * @return {@code true}, если списание выполнено
     */
    public boolean withdraw(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Сумма платежа должна быть положительной");
        }
        while (true) {
            long current = balance.get();
            if (current < amount) {
                return false;
            }
            if (balance.compareAndSet(current, current - amount)) {
                return true;
            }
        }
    }
}
