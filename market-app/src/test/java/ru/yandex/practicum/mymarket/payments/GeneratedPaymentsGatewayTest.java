package ru.yandex.practicum.mymarket.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Тесты сгенерированного клиента платежей без Spring-контекста.
 *
 * <p>Клиент собран OpenAPI Generator под Jackson 2, тогда как приложение живёт
 * на Jackson 3. Тест через настоящий {@link MockWebServer} проверяет именно
 * сериализацию и разбор JSON, а не только бизнес-ветки шлюза.
 */
class GeneratedPaymentsGatewayTest {

    private MockWebServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws Exception {
        server.shutdown();
    }

    @Test
    void readsBalance() {
        server.enqueue(json(200, "{\"balance\":7500}"));

        Long balance = gateway().getBalance().block();

        assertThat(balance).isEqualTo(7500L);
    }

    @Test
    void sendsPaymentAndReadsResponse() throws Exception {
        server.enqueue(json(200, "{\"amount\":1500,\"balance\":6000}"));

        PaymentResult result = gateway().pay(1500).block();

        assertThat(result).isEqualTo(new PaymentResult(1500L, 6000L));
        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/v1/payments");
        assertThat(request.getBody().readUtf8()).contains("\"amount\":1500");
    }

    @Test
    void mapsPaymentRequiredToInsufficientFunds() {
        server.enqueue(json(402, "{\"code\":\"INSUFFICIENT_FUNDS\",\"message\":\"no money\"}"));

        assertThatThrownBy(() -> gateway().pay(1500).block()).isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void mapsBadRequestToInvalidPaymentRequest() {
        server.enqueue(json(400, "{\"code\":\"INVALID_AMOUNT\",\"message\":\"bad amount\"}"));

        assertThatThrownBy(() -> gateway().pay(0).block()).isInstanceOf(InvalidPaymentRequestException.class);
    }

    @Test
    void mapsServerErrorToUnavailable() {
        server.enqueue(json(500, "{\"code\":\"INTERNAL\",\"message\":\"boom\"}"));

        assertThatThrownBy(() -> gateway().pay(1500).block()).isInstanceOf(PaymentsUnavailableException.class);
    }

    @Test
    void mapsTimeoutToUnavailable() {
        server.enqueue(json(200, "{\"amount\":1500,\"balance\":6000}").setBodyDelay(2, TimeUnit.SECONDS));

        assertThatThrownBy(() -> gateway(Duration.ofMillis(200)).pay(1500).block())
                .isInstanceOf(PaymentsUnavailableException.class)
                .hasMessageContaining("вовремя")
                .hasCauseInstanceOf(TimeoutException.class);
    }

    private GeneratedPaymentsGateway gateway() {
        return gateway(Duration.ofSeconds(2));
    }

    private GeneratedPaymentsGateway gateway(Duration timeout) {
        return new GeneratedPaymentsGateway(
                new PaymentsProperties(server.url("/").toString(), timeout));
    }

    private MockResponse json(int status, String body) {
        return new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
