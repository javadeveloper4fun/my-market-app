package ru.yandex.practicum.mymarket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

/**
 * Настройки реактивного управления транзакциями.
 *
 * <p>Spring Boot создаёт {@link ReactiveTransactionManager} для R2DBC, но не
 * создаёт {@link TransactionalOperator}. Оператор нужен там, где транзакцию
 * требуется открыть явно вокруг части реактивной цепочки, а не аннотацией на
 * публичном методе бина.
 */
@Configuration(proxyBeanMethods = false)
public class TransactionConfig {

    /**
     * Создаёт оператор транзакций для явного оборачивания реактивных операций.
     *
     * @param transactionManager менеджер транзакций R2DBC
     * @return оператор транзакций
     */
    @Bean
    public TransactionalOperator transactionalOperator(ReactiveTransactionManager transactionManager) {
        return TransactionalOperator.create(transactionManager);
    }
}
