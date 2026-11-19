package ru.yandex.practicum.mymarket.cache;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

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
     * Маркер пустого поиска.
     *
     * <p>Символ {@code _} не кодируется {@link URLEncoder}, поэтому в непустой
     * строке он дополнительно заменяется на {@code %5F}. Иначе поиск по
     * символу {@code _} дал бы тот же ключ, что и пустой поиск.
     */
    private static final String EMPTY_SEARCH = "_";

    /**
     * Собирает ключ Redis.
     *
     * @return строка ключа
     */
    public String toRedisKey() {
        String searchPart;
        if (search == null || search.isBlank()) {
            searchPart = EMPTY_SEARCH;
        } else {
            String normalizedSearch = search.trim().toLowerCase(Locale.ROOT);
            searchPart =
                    URLEncoder.encode(normalizedSearch, StandardCharsets.UTF_8).replace("_", "%5F");
        }
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
