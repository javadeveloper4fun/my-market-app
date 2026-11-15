package ru.yandex.practicum.mymarket.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Тесты ключей кеша каталога.
 *
 * <p>Ключ читается глазами через {@code redis-cli}, поэтому его формат
 * фиксируется проверкой: смена префикса или порядка частей молча разъедет
 * запись и чтение после деплоя.
 */
class ItemCacheKeyTest {

    @Test
    void versionKeyIsSeparateFromItemKeys() {
        assertThat(ItemsPageKey.versionKey()).isEqualTo("market:items:version");
    }

    @Test
    void blankSearchIsStoredAsUnderscore() {
        assertThat(new ItemsPageKey(0, "", "NO", 1, 10).toRedisKey()).isEqualTo("market:items:0:_:NO:1:10");
        assertThat(new ItemsPageKey(0, "   ", "NO", 1, 10).toRedisKey()).isEqualTo("market:items:0:_:NO:1:10");
    }

    @Test
    void searchIsTrimmedAndColonIsEncoded() {
        String key = new ItemsPageKey(7, " Мяч: красный ", "ALPHA", 2, 5).toRedisKey();

        assertThat(key).startsWith("market:items:7:");
        assertThat(key).endsWith(":ALPHA:2:5");
        assertThat(key).contains("%3A");
        assertThat(key).doesNotContain(" ");
    }

    @Test
    void underscoreSearchDoesNotCollideWithBlankSearch() {
        String blank = new ItemsPageKey(3, "", "PRICE", 1, 20).toRedisKey();
        String underscore = new ItemsPageKey(3, "_", "PRICE", 1, 20).toRedisKey();

        assertThat(blank).isEqualTo("market:items:3:_:PRICE:1:20");
        assertThat(underscore).isEqualTo("market:items:3:%5F:PRICE:1:20");
        assertThat(underscore).isNotEqualTo(blank);
    }

    @Test
    void versionIsPartOfTheKey() {
        assertThat(new ItemsPageKey(3, "Куртка", "PRICE", 1, 20).toRedisKey())
                .isEqualTo("market:items:3:%D0%9A%D1%83%D1%80%D1%82%D0%BA%D0%B0:PRICE:1:20");
    }
}
