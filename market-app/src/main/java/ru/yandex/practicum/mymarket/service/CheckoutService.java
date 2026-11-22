package ru.yandex.practicum.mymarket.service;

import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.payments.PaymentsGateway;
import ru.yandex.practicum.mymarket.security.CurrentUserProvider;

/**
 * Оформление покупки: подготовка заказа, оплата, сохранение.
 *
 * <p>Порядок шагов Prinzipально важен. {@link OrderService#prepareOrder(Long)}
 * читает корзину пользователя и фиксирует цены; оплата выполняется вне
 * транзакции; только после успешной оплаты {@link OrderService#placeOrder}
 * создаёт заказ и очищает корзину. Если платёж отклонён, заказ не создаётся и
 * корзина остаётся нетронутой.
 *
 * <p>Ограничение: если оплата прошла, а сохранение заказа упало, деньги уже
 * списаны. Возврат и идемпотентность в учебном сервисе не реализованы; это
 * описано в README как известное ограничение.
 */
@Service
public class CheckoutService {

    private static final Logger LOG = LoggerFactory.getLogger(CheckoutService.class);

    private final OrderService orderService;
    private final PaymentsGateway paymentsGateway;
    private final CurrentUserProvider currentUserProvider;

    public CheckoutService(OrderService orderService, PaymentsGateway paymentsGateway,
                           CurrentUserProvider currentUserProvider) {
        this.orderService = orderService;
        this.paymentsGateway = paymentsGateway;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Выполняет покупку для текущего авторизованного пользователя.
     *
     * @return идентификатор созданного заказа
     */
    public Mono<Long> checkout() {
        return currentUserProvider
                .getCurrentUser()
                .switchIfEmpty(Mono.error(new EmptyCartException("User is not authenticated")))
                .flatMap(user -> orderService
                        .prepareOrder(user.getId())
                        .flatMap(prepared -> paymentsGateway
                                .pay(prepared.total())
                                .thenReturn(prepared))
                        .flatMap(prepared -> orderService
                                .placeOrder(prepared, user.getId())
                                .onErrorResume(error -> {
                                    String itemIds = prepared.items().stream()
                                            .map(item -> String.valueOf(item.getItemId()))
                                            .collect(Collectors.joining(","));
                                    String snapshot = prepared.items().stream()
                                            .map(item -> item.getItemId() + ":" + item.getTitle() + ":" + item.getPrice() + "x"
                                                    + item.getCount())
                                            .collect(Collectors.joining(","));
                                    LOG.error(
                                            "PAYMENT_SUCCEEDED_ORDER_FAILED amount={} itemsCount={} itemIds=[{}] snapshot=[{}]",
                                            prepared.total(),
                                            prepared.items().size(),
                                            itemIds,
                                            snapshot,
                                            error);
                                    return Mono.error(error);
                                })));
    }
}
