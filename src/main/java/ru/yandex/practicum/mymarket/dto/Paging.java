package ru.yandex.practicum.mymarket.dto;

/**
 * Состояние постраничной навигации витрины.
 *
 * <p>Фабрика принимает размер страницы, её номер и общее число товаров, а не
 * готовый {@code Page}. В блокирующем стеке {@code Page} приносил с собой всё
 * это из коробки, на реактивном же данные приходят отдельными запросами, и
 * {@code Page} просто не из чего собрать.
 *
 * @param pageSize   товаров на странице
 * @param pageNumber номер страницы, начиная с 1
 * @param hasPrevious есть ли предыдущая страница
 * @param hasNext     есть ли следующая страница
 */
public record Paging(int pageSize, int pageNumber, boolean hasPrevious, boolean hasNext) {

    /**
     * Собирает навигацию из границ выборки и общего числа товаров.
     *
     * <p>Нумерация для шаблонов начинается с единицы, тогда как смещение в
     * запросе считается с нуля, поэтому номер страницы увеличен на один.
     *
     * @param pageSize     товаров на странице
     * @param pageNumber   номер страницы, начиная с 1
     * @param totalItems   всего товаров под поисковый запрос
     * @return состояние навигации
     */
    public static Paging of(int pageSize, int pageNumber, long totalItems) {
        // Произведение считается в long: при больших номерах страницы int
        // переполнялся, shown становился отрицательным, и hasNext оставался
        // истинным навсегда. Наблюдалось это так: на странице 2147483647
        // кнопка перехода вперёд вела на страницу -2147483648.
        long shown = (long) pageNumber * pageSize;
        return new Paging(pageSize, pageNumber, pageNumber > 1, shown < totalItems);
    }

    /**
     * Номер следующей страницы для кнопки перехода вперёд.
     *
     * <p>Отдельный метод, а не {@code pageNumber() + 1} в шаблоне: на верхней
     * границе {@code int} прибавление единицы переполняется и даёт отрицательный
     * номер, по которому запрос выглядит осмысленным, хотя это не так. Здесь
     * номер просто не увеличивается — следующей страницы заведомо нет, ведь
     * максимум {@code int} а {@code hasNext} на нём ложно.
     *
     * @return номер следующей страницы
     */
    public int nextPageNumber() {
        return hasNext ? pageNumber + 1 : pageNumber;
    }

    /**
     * Номер предыдущей страницы для кнопки перехода назад.
     *
     * <p>Симметрично {@link #nextPageNumber()}: кнопка показывается только при
     * {@code hasPrevious}, а на первой странице номер остаётся первым, и запрос
     * не уводит на несуществующую страницу 0.
     *
     * @return номер предыдущей страницы
     */
    public int previousPageNumber() {
        return hasPrevious ? pageNumber - 1 : pageNumber;
    }
}
