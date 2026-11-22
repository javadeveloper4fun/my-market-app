package ru.yandex.practicum.mymarket.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.CartView;
import ru.yandex.practicum.mymarket.payments.PaymentsGateway;
import ru.yandex.practicum.mymarket.security.CurrentUserProvider;

/**
 * Собирает данные страницы корзины: содержимое и баланс.
 *
 * <p>Все операции выполняются в контексте текущего пользователя. Недоступность
 * сервиса платежей не должна ломать корзину. Пользователь должен увидеть
 * товары и причину, по которой покупка временно невозможна, поэтому ошибка
 * баланса превращается в {@link CartView} с сообщением.
 */
@Service
public class CartPageService {

    /** Сообщение о недоступности сервиса платежей на странице корзины. */
    public static final String PAYMENTS_UNAVAILABLE_MESSAGE =
            "Сервис платежей недоступен. Оформление заказа временно невозможно.";

    private final CartService cartService;
    private final PaymentsGateway paymentsGateway;
    private final CurrentUserProvider currentUserProvider;

    public CartPageService(CartService cartService, PaymentsGateway paymentsGateway,
                           CurrentUserProvider currentUserProvider) {
        this.cartService = cartService;
        this.paymentsGateway = paymentsGateway;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Возвращает корзину и баланс текущего пользователя.
     *
     * @return страница корзины
     */
    public Mono<CartView> getCartView() {
        return currentUserProvider
                .getCurrentUser()
                .switchIfEmpty(Mono.error(new EmptyCartException("User is not authenticated")))
                .flatMap(user -> cartService
                        .getCart(user.getId())
                        .flatMap(cart -> paymentsGateway
                                .getBalance()
                                .map(balance -> new CartView(cart, balance, null))
                                .onErrorResume(error -> Mono.just(
                                        new CartView(cart, null, PAYMENTS_UNAVAILABLE_MESSAGE)))));
    }
}
