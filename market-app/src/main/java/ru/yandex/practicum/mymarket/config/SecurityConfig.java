package ru.yandex.practicum.mymarket.config;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.logout.RedirectServerLogoutSuccessHandler;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;

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
     * @return цепочка фильтров
     */
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http.csrf(csrf -> csrf.csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse()))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.GET, "/images/**", "/css/**", "/js/**", "/webjars/**")
                        .permitAll()
                        // Точное правило /items/new обязано идти до общего /items/*:
                        // проверка идёт по порядку, и иначе аноним откроет форму добавления товара.
                        .pathMatchers(HttpMethod.GET, "/items/new")
                        .authenticated()
                        .pathMatchers(HttpMethod.GET, "/", "/items", "/items/*", "/login", "/error")
                        .permitAll()
                        .pathMatchers(HttpMethod.POST, "/items", "/items/*", "/images")
                        .authenticated()
                        .pathMatchers("/cart/**", "/orders/**", "/buy")
                        .authenticated()
                        .anyExchange()
                        .authenticated())
                .formLogin(form -> form.loginPage("/login"))
                .logout(logout ->
                        logout.logoutUrl("/logout").logoutSuccessHandler(redirectServerLogoutSuccessHandler()))
                .oauth2Client(Customizer.withDefaults());

        return http.build();
    }
}
