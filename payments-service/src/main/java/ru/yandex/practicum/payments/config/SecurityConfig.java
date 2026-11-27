package ru.yandex.practicum.payments.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Security-конфигурация payments-service (OAuth2 Resource Server).
 *
 * <p>Сервис платежей принимает только запросы от доверенного OAuth2-клиента
 * (market-app) с валидным JWT access token, выданным Authorization Server.
 * Эндпоинты actuator health/info оставлены открытыми для Docker healthcheck.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .pathMatchers("/api/v1/**")
                        .authenticated()
                        .anyExchange()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .build();
    }
}
