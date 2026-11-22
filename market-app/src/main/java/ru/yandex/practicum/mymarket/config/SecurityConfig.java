package ru.yandex.practicum.mymarket.config;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.logout.RedirectServerLogoutSuccessHandler;
import ru.yandex.practicum.mymarket.security.ReactiveUserDetailsServiceImpl;

/**
 * Конфигурация Spring Security для market-app (WebFlux + Thymeleaf).
 *
 * <p>Анонимным пользователям открыты витрина, карточка товара, статика,
 * страница логина и обработка ошибок. Корзина, заказы и оформление покупки —
 * только авторизованным пользователям. Вход — стандартная форма логина Spring
 * Security, выход — полная очистка сессии/контекста безопасности.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * Кодировщик паролей BCrypt.
     *
     * @return кодировщик паролей
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Цепочка фильтров безопасности WebFlux.
     *
     * @param userDetailsService загрузка пользователей из БД
     * @return настроенная {@link SecurityWebFilterChain}
     */
    /**
     * Обработчик успешного logout.
     *
     * @return обработчик редиректа
     */
    @Bean
    public RedirectServerLogoutSuccessHandler redirectServerLogoutSuccessHandler() {
        RedirectServerLogoutSuccessHandler handler = new RedirectServerLogoutSuccessHandler();
        handler.setLogoutSuccessUrl(URI.create("/"));
        return handler;
    }

    /**
     * Цепочка фильтров безопасности.
     *
     * @param http конфигурация HTTP security
     * @param userDetailsService сервис загрузки пользователя
     * @return цепочка фильтров
     */
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http, ReactiveUserDetailsServiceImpl userDetailsService) {
        http
                // CSRF нужен для небезопасных методов (POST), изменяющих состояние (корзина, оформление заказа).
                // Токен CSRF передаётся в Thymeleaf-формах через hidden-поле.
                .csrf(csrf -> csrf.csrfTokenRepository(
                        new org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository()))
                .authorizeExchange(exchanges -> exchanges
                        // Публичное действие витрины: создание товара только для авторизованного пользователя.
                        .pathMatchers("/items/new")
                        .authenticated()
                        // Публичные страницы: витрина, карточка товара, статика, логин, ошибка.
                        .pathMatchers(
                                "/",
                                "/items",
                                "/items/*",
                                "/images/**",
                                "/css/**",
                                "/js/**",
                                "/webjars/**",
                                "/login",
                                "/error")
                        .permitAll()
                        // Закрытые зоны: только для аутентифицированных пользователей.
                        .pathMatchers("/cart/**", "/orders/**", "/buy")
                        .authenticated()
                        .anyExchange()
                        .authenticated())
                // Стандартная форма логина Spring Security.
                .formLogin(form -> form.loginPage("/login"))
                // Выход: очистка WebSession, куки и SecurityContext, редирект на главную.
                .logout(logout ->
                        logout.logoutUrl("/logout").logoutSuccessHandler(redirectServerLogoutSuccessHandler()))
                // OAuth2 Client для получения токена по клиентским credentials для payments-service.
                .oauth2Client(org.springframework.security.config.Customizer.withDefaults());

        return http.build();
    }
}
