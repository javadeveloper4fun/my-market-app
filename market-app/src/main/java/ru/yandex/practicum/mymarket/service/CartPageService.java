package ru.yandex.practicum.mymarket.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.CartView;
import ru.yandex.practicum.mymarket.payments.PaymentsGateway;
import ru.yandex.practicum.mymarket.payments.PaymentsUnavailableException;

/**
 * Собирает данные страницы корзины: содержимое и баланс.
 *
 * <p>Недоступность сервиса платежей не должна ломать корзину. Пользователь
 * должен увидеть товары и причину, по которой покупка временно невозможна,
 * поэтому ошибка баланса превращается в {@link CartView} с сообщением.
 */
@Service
public class CartPageService {

    /** Сообщение о недоступности сервиса платежей на странице корзины. */
    public static final String PAYMENTS_UNAVAILABLE_MESSAGE =
            "Сервис платежей недоступен. Оформление заказа временно невозможно.";

    private final CartService cartService;
    private final PaymentsGateway paymentsGateway;

    public CartPageService(CartService cartService, PaymentsGateway paymentsGateway) {
        this.cartService = cartService;
        this.paymentsGateway = paymentsGateway;
    }

    /**
     * Возвращает корзину и баланс.
     *
     * @return страница корзины
     */
    public Mono<CartView> getCartView() {
        return cartService
                .getCart()
                .flatMap(cart -> paymentsGateway
                        .getBalance()
                        .map(balance -> new CartView(cart, balance, null))
                        .onErrorResume(
                                PaymentsUnavailableException.class,
                                error -> Mono.just(new CartView(cart, null, PAYMENTS_UNAVAILABLE_MESSAGE)))
                        .onErrorResume(error -> Mono.just(new CartView(cart, null, PAYMENTS_UNAVAILABLE_MESSAGE))));
    }
}
