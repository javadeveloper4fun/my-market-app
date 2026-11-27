package ru.yandex.practicum.mymarket.service;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.model.CheckoutAttempt;
import ru.yandex.practicum.mymarket.model.User;
import ru.yandex.practicum.mymarket.payments.AttemptConflictException;
import ru.yandex.practicum.mymarket.payments.InsufficientFundsException;
import ru.yandex.practicum.mymarket.payments.InvalidPaymentRequestException;
import ru.yandex.practicum.mymarket.payments.PaymentResult;
import ru.yandex.practicum.mymarket.payments.PaymentsGateway;
import ru.yandex.practicum.mymarket.payments.PaymentsUnavailableException;
import ru.yandex.practicum.mymarket.repository.CheckoutAttemptRepository;
import ru.yandex.practicum.mymarket.security.CurrentUserProvider;

/**
 * Оформление покупки с одной активной попыткой на пользователя.
 *
 * <p>Заказ создаётся до платежа и сохраняется при отказе или таймауте. Повтор
 * после таймаута приходит с тем же attemptId: сервис платежей гарантирует
 * одно списание на идентификатор попытки, а CAS-переходы не дают продвинуть
 * состояние дважды.
 */
@Service
public class CheckoutService {

    /**
     * Результат оформления: заказ и попытка, с которой можно повторить платёж.
     *
     * @param orderId идентификатор созданного заказа
     * @param attemptId идентификатор попытки оплаты
     */
    public record CheckoutResult(Long orderId, String attemptId) {}

    private static final Logger LOG = LoggerFactory.getLogger(CheckoutService.class);

    private final OrderService orderService;
    private final CheckoutAttemptRepository checkoutAttemptRepository;
    private final PaymentsGateway paymentsGateway;
    private final CurrentUserProvider currentUserProvider;

    public CheckoutService(
            OrderService orderService,
            CheckoutAttemptRepository checkoutAttemptRepository,
            PaymentsGateway paymentsGateway,
            CurrentUserProvider currentUserProvider) {
        this.orderService = orderService;
        this.checkoutAttemptRepository = checkoutAttemptRepository;
        this.paymentsGateway = paymentsGateway;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Оформляет покупку, продолжая переданную попытку или создавая новую.
     *
     * @param attemptId идентификатор попытки после таймаута или null
     * @return идентификатор заказа и попытки
     */
    public Mono<CheckoutResult> checkout(String attemptId) {
        return currentUserProvider
                .getCurrentUser()
                .switchIfEmpty(Mono.error(UserNotAuthenticatedException::new))
                .flatMap(user -> {
                    if (attemptId == null || attemptId.isBlank()) {
                        return startAttempt(user, UUID.randomUUID().toString());
                    }
                    return checkoutAttemptRepository
                            .findByAttemptId(attemptId)
                            .flatMap(existing -> resumeAttempt(user, existing))
                            .switchIfEmpty(Mono.defer(
                                    () -> startAttempt(user, UUID.randomUUID().toString())));
                });
    }

    private Mono<CheckoutResult> startAttempt(User user, String attemptId) {
        return orderService.prepareOrder(user.getId()).flatMap(prepared -> {
            CheckoutAttempt attempt = new CheckoutAttempt(attemptId, user.getId());
            attempt.setPaymentAmount(prepared.total());
            return checkoutAttemptRepository
                    .save(attempt)
                    .flatMap(saved -> orderService
                            .createAwaitingOrder(prepared, user.getId(), attemptId)
                            .flatMap(orderId -> attachOrder(saved, orderId))
                            .flatMap(orderId -> payAndFinish(user, saved, orderId, prepared.total())))
                    .onErrorResume(DuplicateKeyException.class, error -> handleDuplicate(user, attempt, error));
        });
    }

    private Mono<CheckoutResult> handleDuplicate(User user, CheckoutAttempt attempt, DuplicateKeyException error) {
        String constraint = constraintName(error);
        if (constraint == null) {
            return Mono.error(error);
        }
        if (constraint.contains("uq_checkout_attempt_id")) {
            return checkoutAttemptRepository
                    .findByAttemptId(attempt.getAttemptId())
                    .flatMap(existing -> resumeAttempt(user, existing))
                    .switchIfEmpty(Mono.error(error));
        }
        if (constraint.contains("uq_checkout_active_user")) {
            return checkoutAttemptRepository
                    .findByActiveUserId(user.getId())
                    .flatMap(existing -> {
                        if (!Objects.equals(existing.getAttemptId(), attempt.getAttemptId())) {
                            return Mono.error(new AttemptConflictException("Another checkout is already in progress"));
                        }
                        return resumeAttempt(user, existing);
                    })
                    .switchIfEmpty(Mono.error(error));
        }
        return Mono.error(error);
    }

    private Mono<CheckoutResult> resumeAttempt(User user, CheckoutAttempt existing) {
        if (!Objects.equals(existing.getUserId(), user.getId())) {
            return Mono.error(new AttemptConflictException("Checkout attempt belongs to another user"));
        }
        if (!isResumable(existing.getStatus())) {
            return Mono.error(new AttemptConflictException("Checkout attempt is already finalized"));
        }
        if (existing.getOrderId() == null) {
            return Mono.error(new AttemptConflictException("Checkout attempt is still being created"));
        }
        return orderService
                .prepareRetryOrder(existing.getOrderId(), user.getId(), existing.getAttemptId())
                .flatMap(prepared -> {
                    if (existing.getPaymentAmount() != null && existing.getPaymentAmount() != prepared.total()) {
                        return Mono.error(new AttemptConflictException("Checkout amount has changed"));
                    }
                    return payAndFinish(user, existing, existing.getOrderId(), prepared.total());
                });
    }

    private Mono<Long> attachOrder(CheckoutAttempt attempt, Long orderId) {
        return checkoutAttemptRepository
                .attachOrderIfNull(attempt.getId(), orderId)
                .flatMap(updated -> {
                    if (updated != null && updated > 0) {
                        return Mono.just(orderId);
                    }
                    return checkoutAttemptRepository
                            .findById(attempt.getId())
                            .flatMap(fresh -> Objects.equals(fresh.getOrderId(), orderId)
                                    ? Mono.just(orderId)
                                    : Mono.error(new AttemptConflictException("Checkout attempt has another order")));
                });
    }

    private Mono<CheckoutResult> payAndFinish(User user, CheckoutAttempt attempt, Long orderId, long amount) {
        return paymentsGateway
                .pay(user.getId(), amount, attempt.getAttemptId())
                .flatMap(result -> orderService
                        .finalizePaidOrder(orderId, user.getId())
                        .doOnNext(updated -> {
                            if (!Boolean.TRUE.equals(updated)) {
                                LOG.warn("Order already finalized amount={} orderId={}", amount, orderId);
                            }
                        })
                        .onErrorResume(error -> {
                            LOG.error("PAYMENT_SUCCEEDED_ORDER_FAILED amount={} orderId={}", amount, orderId, error);
                            return Mono.error(error);
                        })
                        .flatMap(updated -> transition(attempt, CheckoutAttempt.Status.PAID.name(), null, result)
                                .thenReturn(new CheckoutResult(orderId, attempt.getAttemptId()))))
                .onErrorResume(
                        InsufficientFundsException.class,
                        error -> failOrderAndAttempt(orderId, attempt, CheckoutAttempt.Status.INSUFFICIENT_FUNDS.name())
                                .then(Mono.error(error)))
                .onErrorResume(
                        InvalidPaymentRequestException.class,
                        error -> failOrderAndAttempt(orderId, attempt, CheckoutAttempt.Status.INSUFFICIENT_FUNDS.name())
                                .then(Mono.error(error)))
                .onErrorResume(
                        PaymentsUnavailableException.class,
                        error -> markTimeoutOrderAndAttempt(orderId, attempt)
                                .then(Mono.error(new CheckoutRetryException(
                                        attempt.getAttemptId(), "payments-unavailable", error))));
    }

    private Mono<Void> markTimeoutOrderAndAttempt(long orderId, CheckoutAttempt attempt) {
        return orderService
                .markPaymentTimeout(orderId)
                .doOnNext(updated -> {
                    if (!Boolean.TRUE.equals(updated)) {
                        LOG.warn("Order already finalized or not awaiting orderId={}", orderId);
                    }
                })
                .onErrorResume(error -> {
                    LOG.error(
                            "CHECKOUT_TIMEOUT_MARK_FAILED orderId={} attemptId={}",
                            orderId,
                            attempt.getAttemptId(),
                            error);
                    return Mono.just(false);
                })
                .then(transition(attempt, CheckoutAttempt.Status.PAYMENT_TIMEOUT.name(), attempt.getUserId(), null))
                .then();
    }

    private Mono<Void> failOrderAndAttempt(long orderId, CheckoutAttempt attempt, String status) {
        return orderService
                .markInsufficientFunds(orderId)
                .doOnNext(updated -> {
                    if (!Boolean.TRUE.equals(updated)) {
                        LOG.warn(
                                "Order already finalized amount={} orderId={} status={}",
                                attempt.getPaymentAmount(),
                                orderId,
                                status);
                    }
                })
                .then(transition(attempt, status, null, null))
                .onErrorResume(error -> {
                    LOG.error(
                            "CHECKOUT_CLEANUP_FAILED orderId={} attemptId={}", orderId, attempt.getAttemptId(), error);
                    return Mono.empty();
                })
                .then();
    }

    private Mono<Integer> transition(CheckoutAttempt attempt, String status, Long activeUserId, PaymentResult result) {
        return checkoutAttemptRepository.transitionStatus(
                attempt.getId(),
                status,
                activeUserId,
                attempt.getOrderId(),
                result == null ? null : result.amount(),
                result == null ? null : result.balance(),
                attempt.getStatus());
    }

    private boolean isResumable(String status) {
        return CheckoutAttempt.Status.AWAITING_PAYMENT.name().equals(status)
                || CheckoutAttempt.Status.PAYMENT_TIMEOUT.name().equals(status);
    }

    private String constraintName(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toUpperCase(Locale.ROOT);
                if (normalized.contains("UQ_CHECKOUT_ATTEMPT_ID")) {
                    return "uq_checkout_attempt_id";
                }
                if (normalized.contains("UQ_CHECKOUT_ACTIVE_USER")) {
                    return "uq_checkout_active_user";
                }
            }
            current = current.getCause();
        }
        return null;
    }
}
