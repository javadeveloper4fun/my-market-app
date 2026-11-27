package ru.yandex.practicum.mymarket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
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
import ru.yandex.practicum.mymarket.model.CheckoutAttempt;
import ru.yandex.practicum.mymarket.model.OrderItem;
import ru.yandex.practicum.mymarket.model.User;
import ru.yandex.practicum.mymarket.payments.InsufficientFundsException;
import ru.yandex.practicum.mymarket.payments.InvalidPaymentRequestException;
import ru.yandex.practicum.mymarket.payments.PaymentResult;
import ru.yandex.practicum.mymarket.payments.PaymentsGateway;
import ru.yandex.practicum.mymarket.payments.PaymentsUnavailableException;
import ru.yandex.practicum.mymarket.repository.CheckoutAttemptRepository;
import ru.yandex.practicum.mymarket.security.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    @Mock
    private OrderService orderService;

    @Mock
    private CheckoutAttemptRepository checkoutAttemptRepository;

    @Mock
    private PaymentsGateway paymentsGateway;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private CheckoutService checkoutService;

    @Test
    void createsOrderBeforePaymentAndFinalizesAttempt() {
        User user = user(1L);
        PreparedOrder prepared = preparedOrder();
        stubCurrentUser(user);
        when(orderService.prepareOrder(1L)).thenReturn(Mono.just(prepared));
        when(checkoutAttemptRepository.save(any(CheckoutAttempt.class))).thenAnswer(invocation -> {
            CheckoutAttempt attempt = invocation.getArgument(0);
            attempt.setId(1L);
            return Mono.just(attempt);
        });
        when(orderService.createAwaitingOrder(eq(prepared), eq(1L), anyString()))
                .thenReturn(Mono.just(42L));
        when(checkoutAttemptRepository.attachOrderIfNull(anyLong(), anyLong())).thenReturn(Mono.just(1));
        when(paymentsGateway.pay(eq(1L), eq(2000L), anyString()))
                .thenReturn(Mono.just(new PaymentResult(2000L, 8000L)));
        when(orderService.finalizePaidOrder(42L, 1L)).thenReturn(Mono.just(true));
        stubTransition(1);

        CheckoutService.CheckoutResult result = checkoutService.checkout(null).block();

        assertThat(result).isNotNull();
        assertThat(result.orderId()).isEqualTo(42L);
        assertThat(result.attemptId()).isNotBlank();
        verify(orderService).createAwaitingOrder(eq(prepared), eq(1L), anyString());
        verify(paymentsGateway).pay(eq(1L), eq(2000L), anyString());
        verify(orderService).finalizePaidOrder(42L, 1L);
        verify(checkoutAttemptRepository)
                .transitionStatus(
                        eq(1L),
                        eq(CheckoutAttempt.Status.PAID.name()),
                        nullable(Long.class),
                        nullable(Long.class),
                        eq(2000L),
                        eq(8000L),
                        eq(CheckoutAttempt.Status.AWAITING_PAYMENT.name()));
    }

    @Test
    void marksOrderAndAttemptInsufficientWhenFundsAreInsufficient() {
        User user = user(1L);
        PreparedOrder prepared = preparedOrder();
        stubCurrentUser(user);
        when(orderService.prepareOrder(1L)).thenReturn(Mono.just(prepared));
        when(checkoutAttemptRepository.save(any(CheckoutAttempt.class))).thenAnswer(invocation -> {
            CheckoutAttempt attempt = invocation.getArgument(0);
            attempt.setId(1L);
            return Mono.just(attempt);
        });
        when(orderService.createAwaitingOrder(eq(prepared), eq(1L), anyString()))
                .thenReturn(Mono.just(42L));
        when(checkoutAttemptRepository.attachOrderIfNull(anyLong(), anyLong())).thenReturn(Mono.just(1));
        when(paymentsGateway.pay(eq(1L), eq(2000L), anyString()))
                .thenReturn(Mono.error(new InsufficientFundsException()));
        when(orderService.markInsufficientFunds(42L)).thenReturn(Mono.just(true));
        stubTransition(1);

        assertThatThrownBy(() -> checkoutService.checkout(null).block()).isInstanceOf(InsufficientFundsException.class);

        verify(orderService).createAwaitingOrder(eq(prepared), eq(1L), anyString());
        verify(orderService).markInsufficientFunds(42L);
        verify(orderService, never()).finalizePaidOrder(anyLong(), anyLong());
        verify(checkoutAttemptRepository)
                .transitionStatus(
                        eq(1L),
                        eq(CheckoutAttempt.Status.INSUFFICIENT_FUNDS.name()),
                        nullable(Long.class),
                        nullable(Long.class),
                        nullable(Long.class),
                        nullable(Long.class),
                        eq(CheckoutAttempt.Status.AWAITING_PAYMENT.name()));
    }

    @Test
    void marksOrderAndAttemptInsufficientWhenPaymentRequestIsInvalid() {
        User user = user(1L);
        PreparedOrder prepared = preparedOrder();
        stubCurrentUser(user);
        when(orderService.prepareOrder(1L)).thenReturn(Mono.just(prepared));
        when(checkoutAttemptRepository.save(any(CheckoutAttempt.class))).thenAnswer(invocation -> {
            CheckoutAttempt attempt = invocation.getArgument(0);
            attempt.setId(1L);
            return Mono.just(attempt);
        });
        when(orderService.createAwaitingOrder(eq(prepared), eq(1L), anyString()))
                .thenReturn(Mono.just(42L));
        when(checkoutAttemptRepository.attachOrderIfNull(anyLong(), anyLong())).thenReturn(Mono.just(1));
        when(paymentsGateway.pay(eq(1L), eq(2000L), anyString()))
                .thenReturn(Mono.error(new InvalidPaymentRequestException("bad amount")));
        when(orderService.markInsufficientFunds(42L)).thenReturn(Mono.just(true));
        stubTransition(1);

        assertThatThrownBy(() -> checkoutService.checkout(null).block())
                .isInstanceOf(InvalidPaymentRequestException.class);

        verify(orderService).markInsufficientFunds(42L);
        verify(orderService, never()).finalizePaidOrder(anyLong(), anyLong());
        verify(checkoutAttemptRepository)
                .transitionStatus(
                        eq(1L),
                        eq(CheckoutAttempt.Status.INSUFFICIENT_FUNDS.name()),
                        nullable(Long.class),
                        nullable(Long.class),
                        nullable(Long.class),
                        nullable(Long.class),
                        eq(CheckoutAttempt.Status.AWAITING_PAYMENT.name()));
    }

    @Test
    void doesNotCallPaymentsWhenOrderCreationFails() {
        User user = user(1L);
        PreparedOrder prepared = preparedOrder();
        stubCurrentUser(user);
        when(orderService.prepareOrder(1L)).thenReturn(Mono.just(prepared));
        when(checkoutAttemptRepository.save(any(CheckoutAttempt.class))).thenAnswer(invocation -> {
            CheckoutAttempt attempt = invocation.getArgument(0);
            attempt.setId(1L);
            return Mono.just(attempt);
        });
        when(orderService.createAwaitingOrder(eq(prepared), eq(1L), anyString()))
                .thenReturn(Mono.error(new IllegalStateException("order insert failed")));

        assertThatThrownBy(() -> checkoutService.checkout(null).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("order insert failed");

        verify(paymentsGateway, never()).pay(anyLong(), anyLong(), anyString());
        verify(orderService, never()).markInsufficientFunds(anyLong());
        verify(orderService, never()).finalizePaidOrder(anyLong(), anyLong());
        verify(checkoutAttemptRepository, never())
                .transitionStatus(
                        anyLong(),
                        anyString(),
                        nullable(Long.class),
                        nullable(Long.class),
                        nullable(Long.class),
                        nullable(Long.class),
                        anyString());
    }

    @Test
    void keepsAttemptActiveForRetryWhenPaymentsAreUnavailable() {
        User user = user(1L);
        PreparedOrder prepared = preparedOrder();
        stubCurrentUser(user);
        when(orderService.prepareOrder(1L)).thenReturn(Mono.just(prepared));
        when(checkoutAttemptRepository.save(any(CheckoutAttempt.class))).thenAnswer(invocation -> {
            CheckoutAttempt attempt = invocation.getArgument(0);
            attempt.setId(1L);
            return Mono.just(attempt);
        });
        when(orderService.createAwaitingOrder(eq(prepared), eq(1L), anyString()))
                .thenReturn(Mono.just(42L));
        when(checkoutAttemptRepository.attachOrderIfNull(anyLong(), anyLong())).thenReturn(Mono.just(1));
        when(paymentsGateway.pay(eq(1L), eq(2000L), anyString()))
                .thenReturn(Mono.error(new PaymentsUnavailableException("connection refused")));
        stubTransition(1);

        assertThatThrownBy(() -> checkoutService.checkout(null).block())
                .isInstanceOfSatisfying(CheckoutRetryException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo("payments-unavailable");
                    assertThat(error.getAttemptId()).isNotBlank();
                });

        verify(checkoutAttemptRepository)
                .transitionStatus(
                        eq(1L),
                        eq(CheckoutAttempt.Status.PAYMENT_TIMEOUT.name()),
                        eq(1L),
                        nullable(Long.class),
                        nullable(Long.class),
                        nullable(Long.class),
                        eq(CheckoutAttempt.Status.AWAITING_PAYMENT.name()));
    }

    @Test
    void reusesAttemptWithoutCreatingSecondOrder() {
        User user = user(1L);
        PreparedOrder prepared = preparedOrder();
        CheckoutAttempt existing = attempt(1L, "attempt-9", 1L, CheckoutAttempt.Status.PAYMENT_TIMEOUT, 42L, 2000L);
        stubCurrentUser(user);
        when(checkoutAttemptRepository.findByAttemptId("attempt-9")).thenReturn(Mono.just(existing));
        when(orderService.prepareRetryOrder(42L, 1L, "attempt-9")).thenReturn(Mono.just(prepared));
        when(paymentsGateway.pay(1L, 2000L, "attempt-9")).thenReturn(Mono.just(new PaymentResult(2000L, 8000L)));
        when(orderService.finalizePaidOrder(42L, 1L)).thenReturn(Mono.just(true));
        stubTransition(1);

        CheckoutService.CheckoutResult result =
                checkoutService.checkout("attempt-9").block();

        assertThat(result).isNotNull();
        assertThat(result.orderId()).isEqualTo(42L);
        assertThat(result.attemptId()).isEqualTo("attempt-9");
        verify(orderService, never()).prepareOrder(1L);
        verify(orderService, never()).createAwaitingOrder(any(), anyLong(), anyString());
        verify(paymentsGateway).pay(1L, 2000L, "attempt-9");
    }

    @Test
    void leavesAttemptActiveWhenOrderFinalizationFailsAfterPayment() {
        User user = user(1L);
        PreparedOrder prepared = preparedOrder();
        stubCurrentUser(user);
        when(orderService.prepareOrder(1L)).thenReturn(Mono.just(prepared));
        when(checkoutAttemptRepository.save(any(CheckoutAttempt.class))).thenAnswer(invocation -> {
            CheckoutAttempt attempt = invocation.getArgument(0);
            attempt.setId(1L);
            return Mono.just(attempt);
        });
        when(orderService.createAwaitingOrder(eq(prepared), eq(1L), anyString()))
                .thenReturn(Mono.just(42L));
        when(checkoutAttemptRepository.attachOrderIfNull(anyLong(), anyLong())).thenReturn(Mono.just(1));
        when(paymentsGateway.pay(eq(1L), eq(2000L), anyString()))
                .thenReturn(Mono.just(new PaymentResult(2000L, 8000L)));
        when(orderService.finalizePaidOrder(42L, 1L)).thenReturn(Mono.error(new IllegalStateException("db down")));

        assertThatThrownBy(() -> checkoutService.checkout(null).block()).isInstanceOf(IllegalStateException.class);

        verify(checkoutAttemptRepository, never())
                .transitionStatus(
                        anyLong(),
                        anyString(),
                        nullable(Long.class),
                        nullable(Long.class),
                        nullable(Long.class),
                        nullable(Long.class),
                        anyString());
    }

    @Test
    void doesNotPayWhenCartIsEmpty() {
        User user = user(1L);
        stubCurrentUser(user);
        when(orderService.prepareOrder(1L)).thenReturn(Mono.error(new EmptyCartException("Cart is empty")));

        assertThatThrownBy(() -> checkoutService.checkout(null).block()).isInstanceOf(EmptyCartException.class);

        verify(paymentsGateway, never()).pay(anyLong(), anyLong(), anyString());
    }

    private void stubCurrentUser(User user) {
        when(currentUserProvider.getCurrentUser()).thenReturn(Mono.just(user));
    }

    private void stubTransition(int rows) {
        when(checkoutAttemptRepository.transitionStatus(
                        anyLong(),
                        anyString(),
                        nullable(Long.class),
                        nullable(Long.class),
                        nullable(Long.class),
                        nullable(Long.class),
                        anyString()))
                .thenReturn(Mono.just(rows));
    }

    private CheckoutAttempt attempt(
            long id, String attemptId, long userId, CheckoutAttempt.Status status, Long orderId, long amount) {
        CheckoutAttempt attempt = new CheckoutAttempt(attemptId, userId);
        attempt.setId(id);
        attempt.setStatus(status.name());
        attempt.setOrderId(orderId);
        attempt.setPaymentAmount(amount);
        return attempt;
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
