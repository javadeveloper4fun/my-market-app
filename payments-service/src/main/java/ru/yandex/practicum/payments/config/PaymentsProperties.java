package ru.yandex.practicum.payments.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Настройки сервиса платежей.
 *
 * @param initialBalance начальный баланс счёта в целых рублях
 */
@ConfigurationProperties(prefix = "payments")
public record PaymentsProperties(@DefaultValue("10000") long initialBalance) {}
