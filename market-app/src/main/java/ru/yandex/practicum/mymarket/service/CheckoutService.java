package ru.yandex.practicum.mymarket.service;

import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.payments.PaymentsGateway;

/**
 * Оформление покупки: подготовка заказа, оплата, сохранение.
 *
 * <p>Порядок шагов принципиален. {@link OrderService#prepareOrder()} читает
 * корзину и фиксирует цены; оплата выполняется вне транзакции; только после
 * успешной оплаты {@link OrderService#placeOrder(PreparedOrder)} создаёт заказ
 * и очищает корзину. Если платёж отклонён, заказ не создаётся и корзина
 * остаётся нетронутой.
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

    public CheckoutService(OrderService orderService, PaymentsGateway paymentsGateway) {
        this.orderService = orderService;
        this.paymentsGateway = paymentsGateway;
    }

    /**
     * Выполняет покупку.
     *
     * @return идентификатор созданного заказа
     */
    public Mono<Long> checkout() {
        return orderService
                .prepareOrder()
                .flatMap(prepared -> paymentsGateway.pay(prepared.total()).thenReturn(prepared))
                .flatMap(prepared -> orderService
                        .placeOrder(prepared)
                        .onErrorResume(error -> {
                            String itemIds = prepared.items().stream()
                                    .map(item -> String.valueOf(item.getItemId()))
                                    .collect(Collectors.joining(","));
                            LOG.error(
                                    "PAYMENT_SUCCEEDED_ORDER_FAILED amount={} itemIds=[{}] items={}",
                                    prepared.total(),
                                    itemIds,
                                    prepared.items().size(),
                                    error);
                            return Mono.error(error);
                        }));
    }
}
