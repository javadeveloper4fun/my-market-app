package ru.yandex.practicum.mymarket.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.web.reactive.function.client.WebClient;

class OAuth2PaymentsClientTest {

    private MockWebServer tokenServer;
    private MockWebServer paymentsServer;

    @BeforeEach
    void startServers() throws Exception {
        tokenServer = new MockWebServer();
        tokenServer.start();
        paymentsServer = new MockWebServer();
        paymentsServer.start();
    }

    @AfterEach
    void stopServers() throws Exception {
        paymentsServer.shutdown();
        tokenServer.shutdown();
    }

    @Test
    void sendsBearerTokenToPaymentsService() throws Exception {
        tokenServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"access_token\":\"test-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}"));
        paymentsServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"balance\":7500}"));

        Long balance = gateway().getBalance(1L).block();

        assertThat(balance).isEqualTo(7500L);
        tokenServer.takeRequest();
        RecordedRequest paymentRequest = paymentsServer.takeRequest();
        assertThat(paymentRequest.getHeader("Authorization")).isEqualTo("Bearer test-token");
        assertThat(paymentRequest.getHeader("X-User-Id")).isEqualTo("1");
    }

    @Test
    void mapsUnauthorizedFromPaymentsToUnavailable() {
        tokenServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"access_token\":\"test-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}"));
        paymentsServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"code\":\"UNAUTHORIZED\",\"message\":\"bad token\"}"));

        assertThatThrownBy(() -> gateway().getBalance(1L).block()).isInstanceOf(PaymentsUnavailableException.class);
    }

    private GeneratedPaymentsGateway gateway() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("market-app")
                .clientId("market-app")
                .clientSecret("secret")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri(tokenServer.url("/token").toString())
                .build();
        InMemoryReactiveClientRegistrationRepository registrations =
                new InMemoryReactiveClientRegistrationRepository(registration);
        InMemoryReactiveOAuth2AuthorizedClientService clientService =
                new InMemoryReactiveOAuth2AuthorizedClientService(registrations);
        ReactiveOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(registrations, clientService);
        ((AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager) manager)
                .setAuthorizedClientProvider(ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
                        .clientCredentials()
                        .build());

        var oauth2 = new org.springframework.security.oauth2.client.web.reactive.function.client
                .ServerOAuth2AuthorizedClientExchangeFilterFunction(manager);
        oauth2.setDefaultClientRegistrationId("market-app");
        WebClient webClient = WebClient.builder().filter(oauth2).build();

        return new GeneratedPaymentsGateway(
                webClient, new PaymentsProperties(paymentsServer.url("/").toString(), Duration.ofSeconds(2)));
    }
}
