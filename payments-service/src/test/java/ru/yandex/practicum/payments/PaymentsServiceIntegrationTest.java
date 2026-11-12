package ru.yandex.practicum.payments;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.yandex.practicum.payments.service.BalanceAccount;

/**
 * Интеграционный тест HTTP-цепочки сервиса платежей.
 *
 * <p>Поднимается полный контекст и реальный HTTP-клиент, но без внешних
 * зависимостей: сервис хранит баланс в памяти. Счёт сбрасывается перед каждым
 * тестом, чтобы не переиспользовать побочные эффекты списания.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class PaymentsServiceIntegrationTest {

    @Autowired
    private WebTestClient client;

    @Autowired
    private BalanceAccount account;

    @BeforeEach
    void resetBalance() {
        account.reset();
    }

    @Test
    void returnsBalance() {
        client.get()
                .uri("/api/v1/balance")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.balance")
                .isEqualTo(10_000);
    }

    @Test
    void paysAndUpdatesBalance() {
        client.post()
                .uri("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"amount":1500}
                        """)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.amount")
                .isEqualTo(1500)
                .jsonPath("$.balance")
                .isEqualTo(8500);

        client.get()
                .uri("/api/v1/balance")
                .exchange()
                .expectBody()
                .jsonPath("$.balance")
                .isEqualTo(8500);
    }

    @Test
    void rejectsPaymentWhenFundsInsufficient() {
        client.post()
                .uri("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"amount":15000}
                        """)
                .exchange()
                .expectStatus()
                .isEqualTo(402)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("INSUFFICIENT_FUNDS");
    }
}
