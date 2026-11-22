package ru.yandex.practicum.mymarket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.model.OrderItem;
import ru.yandex.practicum.mymarket.payments.InsufficientFundsException;
import ru.yandex.practicum.mymarket.payments.PaymentResult;
import ru.yandex.practicum.mymarket.payments.PaymentsGateway;

/**
 * Тесты порядка шагов оформления покупки.
 *
 * <p>Главная проверка — {@code placeOrder} не вызывается, если оплата не
 * прошла. Иначе заказ создавался бы без денег, а корзина очищалась бы при
 * отказе платежа.
 */
@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    @Mock
    private OrderService orderService;

    @Mock
    private PaymentsGateway paymentsGateway;

    @InjectMocks
    private CheckoutService checkoutService;

    @Test
    void paysThenPlacesOrder() {
        PreparedOrder prepared = preparedOrder();
        when(orderService.prepareOrder()).thenReturn(Mono.just(prepared));
        when(paymentsGateway.pay(prepared.total())).thenReturn(Mono.just(new PaymentResult(2000L, 8000L)));
        when(orderService.placeOrder(prepared)).thenReturn(Mono.just(42L));

        Long orderId = checkoutService.checkout().block();

        assertThat(orderId).isEqualTo(42L);
        verify(paymentsGateway).pay(2000L);
        verify(orderService).placeOrder(prepared);
    }

    @Test
    void doesNotPlaceOrderWhenPaymentFails() {
        PreparedOrder prepared = preparedOrder();
        when(orderService.prepareOrder()).thenReturn(Mono.just(prepared));
        when(paymentsGateway.pay(prepared.total())).thenReturn(Mono.error(new InsufficientFundsException()));

        assertThatThrownBy(() -> checkoutService.checkout().block()).isInstanceOf(InsufficientFundsException.class);

        verify(orderService, never()).placeOrder(any());
    }

    @Test
    void doesNotPayWhenCartIsEmpty() {
        when(orderService.prepareOrder()).thenReturn(Mono.error(new EmptyCartException("Cart is empty")));

        assertThatThrownBy(() -> checkoutService.checkout().block()).isInstanceOf(EmptyCartException.class);

        verify(paymentsGateway, never()).pay(anyLong());
    }

    private PreparedOrder preparedOrder() {
        return PreparedOrder.of(List.of(new OrderItem(null, 1L, "Куртка", new BigDecimal("1000"), 2)));
    }
}
