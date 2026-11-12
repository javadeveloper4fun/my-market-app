package ru.yandex.practicum.mymarket.payments;

import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.payments.client.api.BalanceApi;
import ru.yandex.practicum.payments.client.api.PaymentsApi;
import ru.yandex.practicum.payments.client.invoker.ApiClient;
import ru.yandex.practicum.payments.client.model.PaymentRequest;
import ru.yandex.practicum.payments.client.model.PaymentResponse;

/**
 * Шлюз к сервису платежей на сгенерированном OpenAPI-клиенте.
 *
 * <p>Клиент создаётся из спецификации {@code api/payments-api.yaml}, поэтому
 * адрес, модели и формат ошибок совпадают с контрактом сервиса. Бизнес-ошибки
 * переводятся в исключения витрины: остальной код не должен зависеть от
 * {@code WebClientResponseException} и HTTP-статусов.
 */
@Component
public class GeneratedPaymentsGateway implements PaymentsGateway {

    private static final Logger LOG = LoggerFactory.getLogger(GeneratedPaymentsGateway.class);

    private final BalanceApi balanceApi;
    private final PaymentsApi paymentsApi;
    private final PaymentsProperties properties;

    public GeneratedPaymentsGateway(PaymentsProperties properties) {
        this.properties = properties;
        ApiClient apiClient = new ApiClient().setBasePath(properties.baseUrl());
        this.balanceApi = new BalanceApi(apiClient);
        this.paymentsApi = new PaymentsApi(apiClient);
    }

    @Override
    public Mono<Long> getBalance() {
        return balanceApi
                .getBalance()
                .timeout(properties.timeout())
                .map(response -> response.getBalance() == null ? 0L : response.getBalance())
                .onErrorMap(this::mapError);
    }

    @Override
    public Mono<PaymentResult> pay(long amount) {
        return paymentsApi
                .createPayment(new PaymentRequest().amount(amount))
                .timeout(properties.timeout())
                .map(this::toResult)
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
        if (error instanceof InsufficientFundsException || error instanceof PaymentsUnavailableException) {
            return error;
        }
        if (error instanceof WebClientResponseException responseException
                && responseException.getStatusCode().value() == HttpStatus.PAYMENT_REQUIRED.value()) {
            return new InsufficientFundsException();
        }
        if (error instanceof TimeoutException) {
            LOG.warn("Сервис платежей не ответил за отведённое время: {}", error.getMessage());
            return new PaymentsUnavailableException("Сервис платежей не ответил вовремя", error);
        }
        LOG.warn("Ошибка обращения к сервису платежей: {}", error.getMessage());
        return new PaymentsUnavailableException("Сервис платежей временно недоступен", error);
    }
}
