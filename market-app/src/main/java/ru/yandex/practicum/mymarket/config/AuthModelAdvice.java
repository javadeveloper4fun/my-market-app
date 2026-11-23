package ru.yandex.practicum.mymarket.config;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import reactor.core.publisher.Mono;

@ControllerAdvice
public class AuthModelAdvice {

    @ModelAttribute("isAuthenticated")
    public Mono<Boolean> isAuthenticated(Mono<Authentication> authentication) {
        return authentication
                .map(auth -> auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken))
                .defaultIfEmpty(false);
    }
}
