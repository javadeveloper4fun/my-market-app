package ru.yandex.practicum.payments.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.payments.config.PaymentsProperties;

@Service
public class BalanceAccount {

    private final ConcurrentMap<Long, AtomicLong> balances = new ConcurrentHashMap<>();
    private final long initialBalance;

    public BalanceAccount(PaymentsProperties properties) {
        if (properties.initialBalance() < 0) {
            throw new IllegalArgumentException("Начальный баланс не может быть отрицательным");
        }
        this.initialBalance = properties.initialBalance();
    }

    public void reset() {
        balances.clear();
    }

    public long getBalance(long userId) {
        return balanceFor(userId).get();
    }

    public long withdraw(long userId, long amount) {
        if (amount <= 0) {
            throw new InvalidAmountException();
        }
        AtomicLong balance = balanceFor(userId);
        while (true) {
            long current = balance.get();
            if (current < amount) {
                throw new InsufficientFundsException(amount, current);
            }
            long remaining = current - amount;
            if (balance.compareAndSet(current, remaining)) {
                return remaining;
            }
        }
    }

    private AtomicLong balanceFor(long userId) {
        return balances.computeIfAbsent(userId, ignored -> new AtomicLong(initialBalance));
    }
}
