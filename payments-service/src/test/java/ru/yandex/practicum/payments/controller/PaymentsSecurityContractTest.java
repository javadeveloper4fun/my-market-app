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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "payments.initial-balance=10000")
class PaymentsSecurityContractTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private BalanceAccount account;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        account.reset();
        client = WebTestClient.bindToApplicationContext(context).apply(springSecurity()).configureClient().build();
    }

    @Test
    void rejectsRequestWithoutJwt() {
        client.get().uri("/api/v1/balance").header("X-User-Id", "1")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void rejectsRequestWithoutUserHeader() {
        client.mutateWith(mockJwt()).get().uri("/api/v1/balance")
                .exchange().expectStatus().isBadRequest();
    }

    @Test
    void balanceIsIsolatedByUserId() {
        client.mutateWith(mockJwt()).post().uri("/api/v1/payments")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"amount\":1500}")
                .exchange().expectStatus().isOk();

        client.mutateWith(mockJwt()).get().uri("/api/v1/balance").header("X-User-Id", "1")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.balance").isEqualTo(8500);
        client.mutateWith(mockJwt()).get().uri("/api/v1/balance").header("X-User-Id", "2")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.balance").isEqualTo(10000);
    }
}
