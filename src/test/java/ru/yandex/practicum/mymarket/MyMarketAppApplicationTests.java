package ru.yandex.practicum.mymarket;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Смоук-тест контекста приложения «Витрина интернет-магазина».
 * Поднимает полный Spring-контекст и проверяет, что он стартует
 * без ошибок (Spring Boot Test, кеширование контекстов).
 * Использует test-профиль и встроенную H2, чтобы сборка проходила
 * без внешней PostgreSQL (развёрнутой в Docker).
 */
@SpringBootTest
@ActiveProfiles("test")
class MyMarketAppApplicationTests {

    @Test
    void contextLoads() {}
}
