package ru.yandex.practicum.mymarket.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import reactor.core.publisher.Mono;

/**
 * Страница входа пользователя в приложение.
 *
 * <p>Spring Security form login перенаправляет на {@code /login}, а после
 * успешной аутентификации — на исходный запрос или главную страницу.
 */
@Controller
public class LoginController {

    @GetMapping("/login")
    public Mono<String> login() {
        return Mono.just("login");
    }
}
