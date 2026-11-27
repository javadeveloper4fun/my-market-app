package ru.yandex.practicum.payments.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.payments.api.BalanceApi;
import ru.yandex.practicum.payments.api.PaymentsApi;
import ru.yandex.practicum.payments.model.BalanceResponse;
import ru.yandex.practicum.payments.model.PaymentRequest;
import ru.yandex.practicum.payments.model.PaymentResponse;
import ru.yandex.practicum.payments.service.AttemptRecord;
import ru.yandex.practicum.payments.service.BalanceAccount;
import ru.yandex.practicum.payments.service.InsufficientFundsException;
import ru.yandex.practicum.payments.service.PaymentAttemptService;

@RestController
public class PaymentsController implements BalanceApi, PaymentsApi {

    private final BalanceAccount account;
    private final PaymentAttemptService attemptService;

    public PaymentsController(BalanceAccount account, PaymentAttemptService attemptService) {
        this.account = account;
        this.attemptService = attemptService;
    }

    @Override
    public Mono<ResponseEntity<BalanceResponse>> getBalance(
            @NotNull @RequestHeader(value = "X-User-Id", required = true) Long xUserId, ServerWebExchange exchange) {
        return Mono.fromSupplier(() -> ResponseEntity.ok(new BalanceResponse(account.getBalance(xUserId))));
    }

    @Override
    public Mono<ResponseEntity<PaymentResponse>> createPayment(
            @NotNull @RequestHeader(value = "X-User-Id", required = true) Long xUserId,
            @NotNull @RequestHeader(value = "X-Attempt-Id", required = true) UUID xAttemptId,
            @Valid @RequestBody Mono<PaymentRequest> paymentRequest,
            ServerWebExchange exchange) {
        return paymentRequest.flatMap(request -> {
            long amount = request.getAmount();
            AttemptRecord record = attemptService.pay(xAttemptId.toString(), xUserId, amount);
            return switch (record.status()) {
                case PAID -> Mono.just(ResponseEntity.ok(new PaymentResponse(amount, record.remainingBalance())));
                case INSUFFICIENT_FUNDS ->
                    Mono.error(new InsufficientFundsException(amount, record.remainingBalance()));
            };
        });
    }
}
