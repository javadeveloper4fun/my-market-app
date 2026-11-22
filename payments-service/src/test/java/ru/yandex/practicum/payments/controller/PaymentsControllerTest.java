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
 * Тесты HTTP-контракта сервиса платежей.
 *
 * <p>Баланс хранится в памяти и меняется при списании, поэтому перед каждым
 * тестом он сбрасывается через {@link BalanceAccount#reset()}. Контекст при
 * этом переиспользуется: пересоздание контекста после каждого теста ради
 * сброса одного поля — лишняя плата за старт приложения.
 */
@SpringBootTest
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
