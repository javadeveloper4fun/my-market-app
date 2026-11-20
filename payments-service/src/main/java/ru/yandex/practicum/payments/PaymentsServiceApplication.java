package ru.yandex.practicum.payments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import ru.yandex.practicum.payments.config.PaymentsProperties;

/**
 * RESTful-сервис платежей витрины интернет-магазина.
 *
 * <p>Сервис реактивный: оба эндпоинта возвращают {@code Mono}, а HTTP-сервер
 * работает на Netty. Баланс хранится в памяти процесса — по условию задания
 * источник баланса может быть любым. Начальное значение задаётся свойством
 * {@code payments.initial-balance}.
 */
@SpringBootApplication
@EnableConfigurationProperties(PaymentsProperties.class)
public class PaymentsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentsServiceApplication.class, args);
    }
}
