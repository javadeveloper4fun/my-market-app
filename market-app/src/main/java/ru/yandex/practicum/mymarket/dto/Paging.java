package ru.yandex.practicum.mymarket.dto;

/**
 * Состояние ограниченной пагинации витрины.
 *
 * <p>Рекорд хранит размер страницы, её номер и общее число товаров, а не
 * готовый {@code Page}. В лекционном стеке {@code Page} приносил с собой ещё
 * и то, что не нужно, но реактивный код должен отдавать отдельными полями, а
 * {@code Page} здесь ни к чему подошёл.
 *
 * @param pageSize    товаров на странице
 * @param pageNumber  номер страницы, начиная с 1
 * @param hasPrevious есть ли предыдущая страница
 * @param hasNext     есть ли следующая страница
 */
public record Paging(int pageSize, int pageNumber, boolean hasPrevious, boolean hasNext) {

    /**
     * Собирает состояние пагинации из границ запроса и общего числа товаров.
     *
     * <p>Размер страницы нормализуется в диапазон {@code 1..PagingLimits.MAX_PAGE_SIZE}, номер -
     * не ниже единицы: параметры приходят из URL и могут быть любыми.
     *
     * @param pageSize    товаров на странице
     * @param pageNumber  номер страницы, начиная с 1
     * @param totalItems  всего товаров при текущем фильтре
     * @return состояние пагинации
     */
    public static Paging of(int pageSize, int pageNumber, long totalItems) {
        int normalizedPageSize = Math.min(Math.max(pageSize, 1), PagingLimits.MAX_PAGE_SIZE);
        int normalizedPageNumber = Math.max(pageNumber, 1);
        // Произведение считаем в long: на границе номера страницы int
        // переполняется, shown становится нулевым, и hasNext остаётся
        // истиной навсегда. Выглядело бы так: на странице 2147483647
        // нажатие «вперёд» вело бы на страницу -2147483648.
        long shown = (long) normalizedPageNumber * normalizedPageSize;
        return new Paging(normalizedPageSize, normalizedPageNumber, normalizedPageNumber > 1, shown < totalItems);
    }

    /**
     * Номер следующей страницы для кнопки «вперёд».
     *
     * <p>Отдельный метод, а не {@code pageNumber() + 1} в шаблоне: на верхней
     * границе {@code int} простейшая запись переполняется и даёт отрицательный
     * номер, на котором счётчик выглядел бы сломанным, хотя дело не в нём. Номер
     * здесь нельзя же увеличивать - следующая страница потеряна там, куда
     * положено {@code int} и {@code hasNext} не по ложат.
     *
     * @return номер следующей страницы
     */
    public int nextPageNumber() {
        return hasNext ? pageNumber + 1 : pageNumber;
    }

    /**
     * Номер предыдущей страницы для кнопки «назад».
     *
     * <p>Симметрично {@link #nextPageNumber()}: кнопка показывается только по
     * {@code hasPrevious}, а на первой странице номер останется тем, и номер
     * не уводит на несуществующую страницу 0.
     *
     * @return номер предыдущей страницы
     */
    public int previousPageNumber() {
        return hasPrevious ? pageNumber - 1 : pageNumber;
    }
}
