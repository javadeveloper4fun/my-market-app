package ru.yandex.practicum.mymarket.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.CartView;
import ru.yandex.practicum.mymarket.payments.PaymentsGateway;
import ru.yandex.practicum.mymarket.security.CurrentUserProvider;

@Service
public class CartPageService {

    /** Сообщение о недоступности платежей. */
    public static final String PAYMENTS_UNAVAILABLE_MESSAGE =
            "Сервис платежей недоступен. Оформление заказа временно невозможно.";

    private final CartService cartService;
    private final PaymentsGateway paymentsGateway;
    private final CurrentUserProvider currentUserProvider;

    public CartPageService(
            CartService cartService, PaymentsGateway paymentsGateway, CurrentUserProvider currentUserProvider) {
        this.cartService = cartService;
        this.paymentsGateway = paymentsGateway;
        this.currentUserProvider = currentUserProvider;
    }

    public Mono<CartView> getCartView() {
        return currentUserProvider
                .getCurrentUser()
                .switchIfEmpty(Mono.error(new EmptyCartException("User is not authenticated")))
                .flatMap(user -> cartService
                        .getCart(user.getId())
                        .flatMap(cart -> paymentsGateway
                                .getBalance(user.getId())
                                .map(balance -> new CartView(cart, balance, null))
                                .onErrorResume(
                                        error -> Mono.just(new CartView(cart, null, PAYMENTS_UNAVAILABLE_MESSAGE)))));
    }
}
