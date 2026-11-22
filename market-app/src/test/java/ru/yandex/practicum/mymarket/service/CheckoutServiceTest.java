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
import ru.yandex.practicum.mymarket.model.User;
import ru.yandex.practicum.mymarket.payments.InsufficientFundsException;
import ru.yandex.practicum.mymarket.payments.PaymentResult;
import ru.yandex.practicum.mymarket.payments.PaymentsGateway;
import ru.yandex.practicum.mymarket.security.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    @Mock
    private OrderService orderService;

    @Mock
    private PaymentsGateway paymentsGateway;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private CheckoutService checkoutService;

    @Test
    void paysThenPlacesOrder() {
        User user = user(1L);
        PreparedOrder prepared = preparedOrder();
        when(currentUserProvider.getCurrentUser()).thenReturn(Mono.just(user));
        when(orderService.prepareOrder(1L)).thenReturn(Mono.just(prepared));
        when(paymentsGateway.pay(1L, prepared.total())).thenReturn(Mono.just(new PaymentResult(2000L, 8000L)));
        when(orderService.placeOrder(prepared, 1L)).thenReturn(Mono.just(42L));

        Long orderId = checkoutService.checkout().block();

        assertThat(orderId).isEqualTo(42L);
        verify(paymentsGateway).pay(1L, 2000L);
        verify(orderService).placeOrder(prepared, 1L);
    }

    @Test
    void doesNotPlaceOrderWhenPaymentFails() {
        User user = user(1L);
        PreparedOrder prepared = preparedOrder();
        when(currentUserProvider.getCurrentUser()).thenReturn(Mono.just(user));
        when(orderService.prepareOrder(1L)).thenReturn(Mono.just(prepared));
        when(paymentsGateway.pay(1L, prepared.total())).thenReturn(Mono.error(new InsufficientFundsException()));

        assertThatThrownBy(() -> checkoutService.checkout().block()).isInstanceOf(InsufficientFundsException.class);

        verify(orderService, never()).placeOrder(any(), anyLong());
    }

    @Test
    void doesNotPayWhenCartIsEmpty() {
        User user = user(1L);
        when(currentUserProvider.getCurrentUser()).thenReturn(Mono.just(user));
        when(orderService.prepareOrder(1L)).thenReturn(Mono.error(new EmptyCartException("Cart is empty")));

        assertThatThrownBy(() -> checkoutService.checkout().block()).isInstanceOf(EmptyCartException.class);

        verify(paymentsGateway, never()).pay(anyLong(), anyLong());
    }

    private PreparedOrder preparedOrder() {
        return PreparedOrder.of(List.of(new OrderItem(null, 1L, "Куртка", new BigDecimal("1000"), 2)));
    }

    private User user(long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        return user;
    }
}
