package ru.yandex.practicum.payments.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.payments.api.BalanceApi;
import ru.yandex.practicum.payments.api.PaymentsApi;
import ru.yandex.practicum.payments.model.BalanceResponse;
import ru.yandex.practicum.payments.model.PaymentRequest;
import ru.yandex.practicum.payments.model.PaymentResponse;
import ru.yandex.practicum.payments.service.BalanceAccount;

/**
 * Обработчик платёжных эндпоинтов.
 *
 * <p>Класс реализует интерфейсы, сгенерированные из OpenAPI-спецификации. Это
 * принцип Contract First: маршруты, коды ответов и модели описаны в
 * {@code api/payments-api.yaml}, а контроллер лишь выполняет операцию.
 */
@RestController
public class PaymentsController implements BalanceApi, PaymentsApi {

    private final BalanceAccount account;

    public PaymentsController(BalanceAccount account) {
        this.account = account;
    }

    @Override
    public Mono<ResponseEntity<BalanceResponse>> getBalance(ServerWebExchange exchange) {
        return Mono.fromSupplier(() -> ResponseEntity.ok(new BalanceResponse(account.getBalance())));
    }

    @Override
    public Mono<ResponseEntity<PaymentResponse>> createPayment(
            @Valid @RequestBody Mono<PaymentRequest> paymentRequest, ServerWebExchange exchange) {
        return paymentRequest.map(request -> {
            long amount = request.getAmount();
            long remaining = account.withdraw(amount);
            return ResponseEntity.ok(new PaymentResponse(amount, remaining));
        });
    }
}
