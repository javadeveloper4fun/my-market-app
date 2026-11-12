package ru.yandex.practicum.mymarket.cache;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Ключ страницы витрины в Redis.
 *
 * <p>Версия в ключе позволяет инвалидировать все страницы одним атомарным
 * {@code INCR} без перебора ключей. Перебор через {@code KEYS} или {@code SCAN}
 * на боевом Redis недопустим: он блокирует сервер на большом пространстве
 * ключей.
 *
 * @param version   версия каталога
 * @param search    нормализованная строка поиска
 * @param sort      нормализованная сортировка
 * @param page      номер страницы
 * @param pageSize  размер страницы
 */
public record ItemsPageKey(long version, String search, String sort, int page, int pageSize) {

    private static final String PREFIX = "market:items:";

    /**
     * Собирает ключ Redis.
     *
     * @return строка ключа
     */
    public String toRedisKey() {
        String searchPart =
                search == null || search.isBlank() ? "_" : URLEncoder.encode(search.trim(), StandardCharsets.UTF_8);
        return PREFIX + version + ':' + searchPart + ':' + sort + ':' + page + ':' + pageSize;
    }

    /**
     * Возвращает ключ версии каталога.
     *
     * @return строка ключа
     */
    public static String versionKey() {
        return PREFIX + "version";
    }
}
