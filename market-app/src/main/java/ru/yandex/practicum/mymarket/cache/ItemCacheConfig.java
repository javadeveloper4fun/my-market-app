package ru.yandex.practicum.mymarket.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Выбор реализации кеша по свойству {@code market.cache.enabled}.
 */
@Configuration
public class ItemCacheConfig {

    /**
     * Заглушка кеша для профилей без Redis.
     *
     * @return кеш, который всегда промахивается
     */
    @Bean
    @ConditionalOnProperty(name = "market.cache.enabled", havingValue = "false")
    public ItemCache noOpItemCache() {
        return new NoOpItemCache();
    }
}
