package ru.yandex.practicum.payments.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.yandex.practicum.payments.service.BalanceAccount;

/**
 * Единственный интеграционный набор тестов HTTP-контракта сервиса платежей.
 *
 * <p>Используется {@code RANDOM_PORT}, чтобы запросы проходили через реальный
 * HTTP-стек Netty (маршалинг контроллера, заголовки, обработка входного JSON на уровне
 * сервера), а Spring-контекст поднимался один раз на весь набор. Ещё один
 * {@code @SpringBootTest}-класс с другим {@code webEnvironment} или без явного
 * порта дал бы второй контекст и удвоил стоимость запуска.
 *
 * <p>Баланс хранится в памяти и меняется при списании, поэтому перед каждым
 * тестом он сбрасывается через {@link BalanceAccount#reset()}. Размер не должен
 * зависеть от порядка выполнения. Начальный баланс зафиксирован явно в
 * свойствах теста, чтобы проверки не зависели от {@code application.yml}.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "payments.initial-balance=10000")
@AutoConfigureWebTestClient
class PaymentsControllerTest {

    @Autowired
    private WebTestClient client;

    @Autowired
    private BalanceAccount account;

    @BeforeEach
    void resetBalance() {
        account.reset();
    }

    @Test
    void returnsInitialBalance() {
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
    void withdrawsAmountAndReturnsRemainingBalance() {
        client.post()
                .uri("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"amount\":1500}")
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
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.balance")
                .isEqualTo(8500);
    }

    @Test
    void rejectsPaymentWhenFundsAreInsufficient() {
        client.post()
                .uri("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"amount\":20000}")
                .exchange()
                .expectStatus()
                .isEqualTo(402)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void rejectsZeroAmountWithContractBody() {
        client.post()
                .uri("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"amount\":0}")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("INVALID_AMOUNT")
                .jsonPath("$.message")
                .isNotEmpty();
    }

    @Test
    void rejectsMissingAmountWithContractBody() {
        client.post()
                .uri("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("INVALID_AMOUNT")
                .jsonPath("$.message")
                .isNotEmpty();
    }

    @Test
    void rejectsMalformedJsonWithContractBody() {
        client.post()
                .uri("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"amount\":")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("INVALID_REQUEST")
                .jsonPath("$.message")
                .isNotEmpty();
    }
}
