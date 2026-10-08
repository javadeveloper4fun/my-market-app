package ru.yandex.practicum.mymarket.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Тесты расчёта состояния пагинации.
 *
 * <p>Проверяется именно арифметика: дефект был не в логике навигации, а в
 * переполнении {@code int} на больших номерах страниц, и обычные страницы его
 * не показывали.
 */
class PagingTest {

    /**
     * Проверяет, что на верхней границе {@code int} следующая страница не
     * уезжает в отрицательные номера.
     *
     * <p>Дефект проявлялся так: произведение номера на размер переполнялось, и
     * кнопка перехода вперёд предлагала страницу {@code -2147483648}. Запрос
     * выглядел бы осмысленным, хотя номер отрицательный.
     */
    @Test
    void nextPageNumberDoesNotOverflowAtMaxInt() {
        Paging paging = Paging.of(10, Integer.MAX_VALUE, 100);

        assertThat(paging.hasNext()).isFalse();
        assertThat(paging.nextPageNumber()).isEqualTo(Integer.MAX_VALUE);
    }

    /**
     * Проверяет, что переполнения нет и на границе, где произведение ещё
     * помещается, но следующая страница уже за пределами каталога.
     */
    @Test
    void hasNextBecomesFalseWhenProductExceedsTotal() {
        Paging paging = Paging.of(10, 214_748_364, 100);

        assertThat(paging.hasNext()).isFalse();
    }

    /**
     * Проверяет обычное поведение, чтобы исправление не сломало его: на второй
     * странице каталога из трёх страниц следующая и предыдущая есть.
     */
    @Test
    void navigationIsUsableOnRegularPage() {
        Paging paging = Paging.of(10, 2, 25);

        assertThat(paging.hasPrevious()).isTrue();
        assertThat(paging.hasNext()).isTrue();
        assertThat(paging.nextPageNumber()).isEqualTo(3);
        assertThat(paging.previousPageNumber()).isEqualTo(1);
    }

    /**
     * Проверяет, что на первой странице кнопка назад не предлагает нулевую
     * страницу, а на последней кнопка вперёд не выходит за каталог.
     */
    @Test
    void navigationStaysInsideCatalog() {
        Paging first = Paging.of(10, 1, 25);
        Paging last = Paging.of(10, 3, 25);

        assertThat(first.hasPrevious()).isFalse();
        assertThat(first.previousPageNumber()).isEqualTo(1);
        assertThat(last.hasNext()).isFalse();
        assertThat(last.nextPageNumber()).isEqualTo(3);
    }
}
