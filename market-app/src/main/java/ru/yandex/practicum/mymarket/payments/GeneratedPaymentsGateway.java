package ru.yandex.practicum.mymarket.payments;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.payments.client.model.BalanceResponse;
import ru.yandex.practicum.payments.client.model.PaymentRequest;
import ru.yandex.practicum.payments.client.model.PaymentResponse;

@Component
public class GeneratedPaymentsGateway implements PaymentsGateway {

    private static final Logger LOG = LoggerFactory.getLogger(GeneratedPaymentsGateway.class);
    private static final String USER_HEADER = "X-User-Id";
    private static final String ATTEMPT_HEADER = "X-Attempt-Id";

    private final WebClient paymentsWebClient;
    private final PaymentsProperties properties;

    public GeneratedPaymentsGateway(WebClient paymentsWebClient, PaymentsProperties properties) {
        this.paymentsWebClient = paymentsWebClient;
        this.properties = properties;
    }

    @Override
    public Mono<Long> getBalance(long userId) {
        return paymentsWebClient
                .get()
                .uri(properties.baseUrl() + "/api/v1/balance")
                .header(USER_HEADER, String.valueOf(userId))
                .retrieve()
                .bodyToMono(BalanceResponse.class)
                .map(response -> response.getBalance() == null ? 0L : response.getBalance())
                .timeout(properties.timeout())
                .onErrorMap(this::mapError);
    }

    @Override
    public Mono<PaymentResult> pay(long userId, long amount, String attemptId) {
        return paymentsWebClient
                .post()
                .uri(properties.baseUrl() + "/api/v1/payments")
                .header(USER_HEADER, String.valueOf(userId))
                .header(ATTEMPT_HEADER, attemptId)
                .bodyValue(new PaymentRequest().amount(amount))
                .retrieve()
                .bodyToMono(PaymentResponse.class)
                .map(this::toResult)
                .timeout(properties.timeout())
                .onErrorMap(this::mapError);
    }

    private PaymentResult toResult(PaymentResponse response) {
        long amount = response.getAmount() == null ? 0L : response.getAmount();
        long balance = response.getBalance() == null ? 0L : response.getBalance();
        return new PaymentResult(amount, balance);
    }

    private Throwable mapError(Throwable error) {
        if (error instanceof PaymentsUnavailableException
                || error instanceof InsufficientFundsException
                || error instanceof InvalidPaymentRequestException
                || error instanceof AttemptConflictException) {
            return error;
        }
        if (error instanceof WebClientResponseException responseException) {
            if (responseException.getStatusCode() == HttpStatus.PAYMENT_REQUIRED) {
                return new InsufficientFundsException();
            }
            if (responseException.getStatusCode() == HttpStatus.CONFLICT) {
                return new AttemptConflictException(
                        "Сервис платежей вернул конфликт попытки (attemptId уже используется)");
            }
            if (responseException.getStatusCode() == HttpStatus.UNAUTHORIZED
                    || responseException.getStatusCode() == HttpStatus.FORBIDDEN) {
                LOG.warn("Payments service returned status {}", responseException.getStatusCode());
                return new PaymentsUnavailableException("Сервис платежей временно недоступен", responseException);
            }
            if (responseException.getStatusCode().is4xxClientError()) {
                return new InvalidPaymentRequestException("Сервис платежей отклонил запрос оплаты");
            }
            LOG.warn("Payments service returned status {}", responseException.getStatusCode());
            return new PaymentsUnavailableException("Сервис платежей временно недоступен", responseException);
        }
        if (isTimeout(error)) {
            LOG.warn("Payments service timed out after {}", properties.timeout());
            return new PaymentsUnavailableException("Сервис платежей не ответил вовремя", error);
        }
        LOG.warn("Payments service call failed", error);
        return new PaymentsUnavailableException("Сервис платежей временно недоступен", error);
    }

    private boolean isTimeout(Throwable error) {
        if (error instanceof java.util.concurrent.TimeoutException) {
            return true;
        }
        Throwable cause = error.getCause();
        return cause != null && cause != error && isTimeout(cause);
    }
}
