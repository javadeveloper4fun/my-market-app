package ru.yandex.practicum.mymarket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Главный класс Spring Boot-приложения «Витрина интернет-магазина».
 * {@code @SpringBootApplication} включает автоматическую настройку,
 * сканирование компонентов и конфигурацию приложения.
 *
 * Спринт 5: блокирующий стек (Spring Web MVC + Spring Data JPA) —
 * приложение запускается во встроенном сервлет-контейнере (Tomcat)
 * и упаковывается в Executable Jar.
 */
@SpringBootApplication
public class MyMarketAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyMarketAppApplication.class, args);
    }
}
