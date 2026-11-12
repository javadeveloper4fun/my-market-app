package ru.yandex.practicum.mymarket.dto;

/**
 * Корзина вместе с состоянием платежей для страницы.
 *
 * @param cart         содержимое корзины
 * @param balance      баланс или {@code null}, если сервис недоступен
 * @param paymentError сообщение об ошибке платежей или {@code null}
 */
public record CartView(CartPage cart, Long balance, String paymentError) {

    /**
     * Можно ли показать активную кнопку покупки.
     *
     * <p>Проверка на сервере всё равно обязательна: кнопка — только UX, а
     * {@code POST /buy} можно отправить и без неё.
     *
     * @return {@code true}, если корзина не пуста, баланс известен и достаточен
     */
    public boolean canPay() {
        return !cart.items().isEmpty() && balance != null && balance >= cart.total();
    }

    /**
     * Хватает ли средств при известном балансе.
     *
     * @return {@code true}, если баланс известен и меньше суммы корзины
     */
    public boolean insufficientFunds() {
        return balance != null && balance < cart.total();
    }
}
