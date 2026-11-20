package ru.yandex.practicum.mymarket.support;

import org.testcontainers.containers.GenericContainer;

/**
 * Один контейнер Redis на весь тестовый JVM.
 *
 * <p>Поле не помечено {@code @Container}: Testcontainers останавливает такие
 * контейнеры после каждого тестового класса. Spring кеширует контекст между
 * классами с одинаковыми настройками, поэтому после перезапуска контейнера
 * контекст продолжал бы смотреть на старый порт. Контейнер запускается один
 * раз при первом обращении, а Testcontainers останавливает его при завершении
 * JVM.
 */
public final class RedisTestContainer {

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7.4.2-bookworm").withExposedPorts(6379);

    static {
        REDIS.start();
    }

    private RedisTestContainer() {}

    /**
     * Возвращает хост контейнера.
     *
     * @return хост
     */
    public static String host() {
        return REDIS.getHost();
    }

    /**
     * Возвращает опубликованный порт Redis.
     *
     * @return порт
     */
    public static int port() {
        return REDIS.getMappedPort(6379);
    }
}
