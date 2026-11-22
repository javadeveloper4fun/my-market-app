package ru.yandex.practicum.mymarket.payments;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Настройки интеграции витрины с сервисом платежей.
 *
 * @param baseUrl адрес сервиса платежей
 * @param timeout предельное время ожидания ответа
 */
@ConfigurationProperties(prefix = "market.payments")
public record PaymentsProperties(
        @DefaultValue("http://localhost:8081") String baseUrl,
        @DefaultValue("PT2S") Duration timeout) {}
