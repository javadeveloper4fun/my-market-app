package ru.yandex.practicum.payments.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

/**
 * Идемпотентное хранилище попыток оплаты (учебное упрощение, по образцу {@link BalanceAccount}).
 * <p>
 * Ключ карты — attemptId (X-Attempt-Id). Первая вставка выполняет реальное списание через
 * {@link BalanceAccount}; все повторные запросы с тем же attemptId возвращают сохранённый
 * результат без повторного списания. Атомарность обеспечивается {@link ConcurrentMap#compute},
 * поэтому два параллельных запроса с одним attemptId дают ровно один withdraw.
 */
@Service
public class PaymentAttemptService {

    private final ConcurrentMap<String, AttemptRecord> attempts = new ConcurrentHashMap<>();
    private final BalanceAccount account;

    public PaymentAttemptService(BalanceAccount account) {
        this.account = account;
    }

    /**
     * Обработать платёж идемпотентно по attemptId.
     *
     * @param attemptId уникальный идентификатор попытки (X-Attempt-Id)
     * @param userId    идентификатор пользователя витрины
     * @param amount    сумма списания
     * @return запись результата AttemptRecord
     * @throws AttemptConflictException если attemptId уже занят другим пользователем/суммой
     */
    public AttemptRecord pay(String attemptId, long userId, long amount) {
        return attempts.compute(attemptId, (key, existing) -> {
            if (existing != null) {
                if (existing.userId() == userId && existing.amount() == amount) {
                    return existing; // идемпотентный повтор — сохранённый результат
                }
                throw new AttemptConflictException("Attempt " + attemptId + " already used by user " + existing.userId()
                        + " with amount " + existing.amount()
                        + ", requested user=" + userId + " amount=" + amount);
            }
            // Новая попытка — списываем ровно один раз
            long remaining;
            try {
                remaining = account.withdraw(userId, amount);
                return new AttemptRecord(userId, amount, AttemptRecord.Status.PAID, remaining);
            } catch (InsufficientFundsException e) {
                return new AttemptRecord(
                        userId, amount, AttemptRecord.Status.INSUFFICIENT_FUNDS, e.getCurrentBalance());
            }
        });
    }

    /**
     * Возвращает текущий баланс пользователя (для проверок в тестах).
     *
     * @param userId идентификатор пользователя витрины
     * @return текущий баланс пользователя
     */
    public long accountBalance(long userId) {
        return account.getBalance(userId);
    }

    /** Сбросить все попытки (используется в тестах). */
    public void reset() {
        attempts.clear();
    }
}
