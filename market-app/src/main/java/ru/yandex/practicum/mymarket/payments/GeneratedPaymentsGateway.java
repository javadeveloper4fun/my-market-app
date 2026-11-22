package ru.yandex.practicum.mymarket.payments;

import java.util.concurrent.TimeoutException;
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

/**
 * Шлюз к сервису платежей на сгенерированном OpenAPI-клиенте.
 *
 * <p>Клиент создаётся из спецификации {@code api/payments-api.yaml}, поэтому
 * адрес, модели и формат ошибок совпадают с контрактом сервиса. Бизнес-ошибки
 * переводятся в исключения витрины: остальной код не должен зависеть от
 * {@code WebClientResponseException} и HTTP-статусов.
 *
 * <p>Запросы в payments-service выполняются через {@link WebClient} с
 * OAuth2 Bearer-токеном (Client Credentials Flow), который добавляется
 * фильтром {@link ru.yandex.practicum.mymarket.config.OAuth2ClientConfig#paymentsWebClient()}.
 */
@Component
public class GeneratedPaymentsGateway implements PaymentsGateway {

    private static final Logger LOG = LoggerFactory.getLogger(GeneratedPaymentsGateway.class);

    private final WebClient paymentsWebClient;
    private final PaymentsProperties properties;

    public GeneratedPaymentsGateway(WebClient paymentsWebClient, PaymentsProperties properties) {
        this.paymentsWebClient = paymentsWebClient;
        this.properties = properties;
    }

    @Override
    public Mono<Long> getBalance() {
        return paymentsWebClient
                .get()
                .uri(properties.baseUrl() + "/api/v1/balance")
                .retrieve()
                .bodyToMono(BalanceResponse.class)
                .map(response -> response.getBalance() == null ? 0L : response.getBalance())
                .timeout(properties.timeout())
                .onErrorMap(this::mapError);
    }

    @Override
    public Mono<PaymentResult> pay(long amount) {
        return paymentsWebClient
                .post()
                .uri(properties.baseUrl() + "/api/v1/payments")
                .bodyValue(new PaymentRequest().amount(amount))
                .retrieve()
                .bodyToMono(PaymentResponse.class)
                .map(this::toResult)
                .timeout(properties.timeout())
                .onErrorMap(this::mapError);
    }

    /**
     * Преобразует ответ сервиса в результат витрины.
     *
     * @param response ответ сервиса
     * @return результат списания
     */
    private PaymentResult toResult(PaymentResponse response) {
        long paid = response.getAmount() == null ? 0L : response.getAmount();
        long balance = response.getBalance() == null ? 0L : response.getBalance();
        return new PaymentResult(paid, balance);
    }

    /**
     * Переводит технические ошибки клиента в ошибки предметной области.
     *
     * @param error исходная ошибка
     * @return ошибка для бизнес-логики
     */
    private Throwable mapError(Throwable error) {
        if (error instanceof InsufficientFundsException
                || error instanceof PaymentsUnavailableException
                || error instanceof InvalidPaymentRequestException) {
            return error;
        }
        if (error instanceof WebClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            if (status == HttpStatus.PAYMENT_REQUIRED.value()) {
                return new InsufficientFundsException();
            }
            if (status == HttpStatus.BAD_REQUEST.value()) {
                LOG.warn("Сервис платежей отклонил запрос: {}", responseException.getResponseBodyAsString());
                return new InvalidPaymentRequestException("Сервис платежей отклонил сумму платежа", error);
            }
        }
        if (error instanceof TimeoutException) {
            LOG.warn("Сервис платежей не ответил за отведённое время: {}", error.getMessage());
            return new PaymentsUnavailableException("Сервис платежей не ответил вовремя", error);
        }
        LOG.warn("Ошибка обращения к сервису платежей: {}", error.getMessage());
        return new PaymentsUnavailableException("Сервис платежей временно недоступен", error);
    }
}
