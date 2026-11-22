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
     * Шифрование паролей: BCrypt. Пароли в БД хранят только в зашифрованном виде.
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
     * После выхода перенаправляет пользователя на главную страницу.
     */
    @Bean
    public RedirectServerLogoutSuccessHandler redirectServerLogoutSuccessHandler() {
        RedirectServerLogoutSuccessHandler handler = new RedirectServerLogoutSuccessHandler();
        handler.setLogoutSuccessUrl(URI.create("/"));
        return handler;
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http,
                                                            ReactiveUserDetailsServiceImpl userDetailsService) throws Exception {
        http
            // CSRF включён для браузерных форм (логин, изменения корзины, оформление заказа).
            // Токен CSRF передаётся в Thymeleaf-формах через hidden-поле.
            .csrf(csrf -> csrf.csrfTokenRepository(
                    new org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository()))
            .authorizeExchange(exchanges -> exchanges
                // Публичные страницы: витрина, карточка товара, статика, логин, ошибки.
                .pathMatchers("/", "/items", "/items/**", "/images/**", "/css/**", "/js/**",
                        "/webjars/**", "/login", "/error").permitAll()
                // Защищённые зоны: только для авторизованных пользователей.
                .pathMatchers("/cart/**", "/orders/**", "/buy").authenticated()
                .anyExchange().authenticated()
            )
            // Стандартная форма логина Spring Security.
            .formLogin(form -> form
                .loginPage("/login")
            )
            // Выход: очистка WebSession, куки и SecurityContext, редирект на главную.
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessHandler(redirectServerLogoutSuccessHandler())
            )
            // OAuth2 Client для получения токена при вызовах payments-service (настраивается отдельно на Этапе 3).
            .oauth2Client(org.springframework.security.config.Customizer.withDefaults());

        return http.build();
    }
}
