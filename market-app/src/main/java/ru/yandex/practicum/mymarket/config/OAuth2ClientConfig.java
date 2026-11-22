package ru.yandex.practicum.mymarket.config;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Конфигурация OAuth2 Client для market-app.
 *
 * <p>Реализует Client Credentials Flow: перед каждым запросом в
 * payments-service фильтр запрашивает access-токен у Keycloak
 * (Authorization Server) и добавляет его в заголовок Authorization: Bearer.
 * Токен кэшируется в памяти на время жизни, чтобы не запрашивать его при
 * каждом вызове payments-service.
 */
@Configuration
public class OAuth2ClientConfig {

    @Value("${market.oauth2.token-uri:http://keycloak:8080/realms/market/protocol/openid-connect/token}")
    private String tokenUri;

    @Value("${market.oauth2.client-id:market-app}")
    private String clientId;

    @Value("${market.oauth2.client-secret:market-secret}")
    private String clientSecret;

    /** Кэш токена: значение + время истечения. */
    private volatile String cachedToken;
    private volatile Instant tokenExpiry;

    /**
     * WebClient для вызовов payments-service с автоматической подстановкой
     * OAuth2 Bearer-токена.
     */
    @Bean
    public WebClient paymentsWebClient() {
        ExchangeFilterFunction oauth2Filter = (request, next) -> {
            return tokenMono()
                    .flatMap(token -> {
                        var modifiedRequest = org.springframework.web.reactive.function.client.ClientRequest.from(request)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .build();
                        return next.exchange(modifiedRequest);
                    });
        };
        return WebClient.builder()
                .filter(oauth2Filter)
                .build();
    }

    /**
     * Запрашивает access-токен у Authorization Server по Client Credentials Flow.
     * Токен кэшируется в памяти на время жизни.
     */
    private synchronized Mono<String> tokenMono() {
        Instant now = Instant.now();
        if (cachedToken != null && tokenExpiry != null && now.isBefore(tokenExpiry.minusSeconds(30))) {
            return Mono.just(cachedToken);
        }
        return WebClient.create()
                .post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(toFormData())
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    String token = (String) response.getOrDefault("access_token", "");
                    Object expiresInObj = response.getOrDefault("expires_in", 300L);
                    long expiresIn = expiresInObj instanceof Number
                            ? ((Number) expiresInObj).longValue()
                            : 300L;
                    cachedToken = token;
                    tokenExpiry = now.plusSeconds(expiresIn);
                    return token;
                });
    }

    private String toFormData() {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "client_credentials");
        form.put("client_id", clientId);
        form.put("client_secret", clientSecret);
        return form.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + "&" + b)
                .orElse("");
    }
}
