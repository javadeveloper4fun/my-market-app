package ru.yandex.practicum.payments.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
import ru.yandex.practicum.payments.service.BalanceAccount;

@RestController
public class PaymentsController implements BalanceApi, PaymentsApi {

    private final BalanceAccount account;

    public PaymentsController(BalanceAccount account) {
        this.account = account;
    }

    @Override
    public Mono<ResponseEntity<BalanceResponse>> getBalance(
            @NotNull @RequestHeader(value = "X-User-Id", required = true) Long xUserId, ServerWebExchange exchange) {
        return Mono.fromSupplier(() -> ResponseEntity.ok(new BalanceResponse(account.getBalance(xUserId))));
    }

    @Override
    public Mono<ResponseEntity<PaymentResponse>> createPayment(
            @NotNull @RequestHeader(value = "X-User-Id", required = true) Long xUserId,
            @Valid @RequestBody Mono<PaymentRequest> paymentRequest,
            ServerWebExchange exchange) {
        return paymentRequest.map(request -> {
            long amount = request.getAmount();
            long remaining = account.withdraw(xUserId, amount);
            return ResponseEntity.ok(new PaymentResponse(amount, remaining));
        });
    }
}
