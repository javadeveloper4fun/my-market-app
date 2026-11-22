package ru.yandex.practicum.payments.controller;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.yandex.practicum.payments.service.BalanceAccount;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "payments.initial-balance=10000")
class PaymentsControllerTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private BalanceAccount account;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        account.reset();
        client = WebTestClient.bindToApplicationContext(context)
                .apply(springSecurity())
                .configureClient()
                .build();
    }

    @Test
    void returnsInitialBalance() {
        client.mutateWith(mockJwt())
                .get()
                .uri("/api/v1/balance")
                .header("X-User-Id", "1")
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
        client.mutateWith(mockJwt())
                .post()
                .uri("/api/v1/payments")
                .header("X-User-Id", "1")
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

        client.mutateWith(mockJwt())
                .get()
                .uri("/api/v1/balance")
                .header("X-User-Id", "1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.balance")
                .isEqualTo(8500);
    }

    @Test
    void rejectsPaymentWhenFundsAreInsufficient() {
        client.mutateWith(mockJwt())
                .post()
                .uri("/api/v1/payments")
                .header("X-User-Id", "1")
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
        client.mutateWith(mockJwt())
                .post()
                .uri("/api/v1/payments")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"amount\":0}")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("INVALID_AMOUNT");
    }

    @Test
    void rejectsMissingAmountWithContractBody() {
        client.mutateWith(mockJwt())
                .post()
                .uri("/api/v1/payments")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("INVALID_AMOUNT");
    }

    @Test
    void rejectsMalformedJsonWithContractBody() {
        client.mutateWith(mockJwt())
                .post()
                .uri("/api/v1/payments")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"amount\":")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("INVALID_REQUEST");
    }

    @Test
    void rejectsUnauthenticatedRequest() {
        client.get()
                .uri("/api/v1/balance")
                .header("X-User-Id", "1")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }
}
