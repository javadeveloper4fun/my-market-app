package ru.yandex.practicum.mymarket.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Настройки кеша товаров.
 *
 * @param ttl время жизни записи в Redis
 */
@ConfigurationProperties(prefix = "market.cache.items")
public record ItemCacheProperties(@DefaultValue("PT5M") Duration ttl) {}
