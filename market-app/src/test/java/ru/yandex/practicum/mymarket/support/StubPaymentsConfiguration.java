package ru.yandex.practicum.mymarket.support;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.payments.InsufficientFundsException;
import ru.yandex.practicum.mymarket.payments.PaymentResult;
import ru.yandex.practicum.mymarket.payments.PaymentsGateway;
import ru.yandex.practicum.mymarket.payments.PaymentsUnavailableException;

/**
 * Заглушка платежного шлюза для интеграционных тестов.
 *
 * <p>Полный контекст не должен ходить в настоящий сервис платежей: тест обязан
 * работать без второго процесса. Бин помечен {@link Primary}, поэтому заменяет
 * сгенерированный шлюз, но не требует выключать его компонент.
 */
@TestConfiguration(proxyBeanMethods = false)
public class StubPaymentsConfiguration {

    /**
     * Создаёт шлюз с большим начальным балансом.
     *
     * @return заглушка платежей
     */
    @Bean
    @Primary
    public PaymentsGateway stubPaymentsGateway() {
        return new StubPaymentsGateway(1_000_000L);
    }

    /**
     * Потокобезопасная заглушка с тем же контрактом, что и настоящий шлюз.
     *
     * <p>Баланс и признак недоступности меняются из теста: сценарии «недостаточно
     * средств» и «сервис недоступен» иначе потребовали бы второго контекста.
     */
    public static final class StubPaymentsGateway implements PaymentsGateway {

        private final AtomicLong balance;
        private volatile boolean unavailable;

        StubPaymentsGateway(long initialBalance) {
            this.balance = new AtomicLong(initialBalance);
        }

        /**
         * Задаёт баланс заглушки.
         *
         * @param value новый баланс
         */
        public void setBalance(long value) {
            balance.set(value);
        }

        /**
         * Включает или выключает режим недоступности сервиса.
         *
         * @param value {@code true} — заглушка отвечает ошибкой
         */
        public void setUnavailable(boolean value) {
            unavailable = value;
        }

        @Override
        public Mono<Long> getBalance() {
            if (unavailable) {
                return Mono.error(new PaymentsUnavailableException("stub unavailable"));
            }
            return Mono.fromSupplier(balance::get);
        }

        @Override
        public Mono<PaymentResult> pay(long amount) {
            if (unavailable) {
                return Mono.error(new PaymentsUnavailableException("stub unavailable"));
            }
            return Mono.fromCallable(() -> {
                long current = balance.get();
                if (current < amount) {
                    throw new InsufficientFundsException();
                }
                balance.set(current - amount);
                return new PaymentResult(amount, current - amount);
            });
        }
    }
}
