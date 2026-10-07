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
        int shown = pageNumber * pageSize;
        return new Paging(pageSize, pageNumber, pageNumber > 1, shown < totalItems);
    }
}
