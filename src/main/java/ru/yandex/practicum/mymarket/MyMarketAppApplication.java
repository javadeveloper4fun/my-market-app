package ru.yandex.practicum.mymarket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;

/**
 * Главный класс Spring Boot-приложения «Витрина интернет-магазина».
 * {@code @SpringBootApplication} включает автоматическую настройку,
 * сканирование компонентов и конфигурацию приложения.
 *
 * <p>Спринт 6 — реактивный стек: WebFlux и Spring Data R2DBC. Приложение
 * запускается на Netty, а не в сервлетном контейнере, и упаковывается в
 * Executable Jar. Прежний блокирующий стек (Spring Web MVC + Spring Data JPA)
 * больше не используется.
 *
 * <p>Тема 10/11 «Конфигурирование и тестирование WebFlux», урок 1/4: подключение
 * {@code spring-boot-starter-webflux} поднимает из коробки реактивный сервер —
 * по умолчанию Netty, слушающий порт {@code server.port}, — оба стиля
 * маршрутизации, реактивные кодеки, бин {@code WebClient.Builder} и раздачу
 * статических ресурсов из {@code classpath:/static}. Шаблоны отрисовывает
 * {@code ThymeleafViewResolver}: он настраивается автоматически вместе со
 * стартером Thymeleaf, поэтому собственного бина {@code ViewResolver}
 * в проекте нет.
 *
 * <p>Здесь же исключена автоконфигурация
 * {@link DataSourceTransactionManagerAutoConfiguration}. Исключение вызвано
 * тем, что приложению нужен {@code DataSource} только ради Liquibase: см.
 * {@link ru.yandex.practicum.mymarket.config.LiquibaseDataSourceConfig}, где
 * он объявлен вручную, потому что автоконфигурация JDBC отключается при
 * наличии бина R2DBC. Как только бин {@code DataSource} появляется, эта
 * автоконфигурация начинает создавать JDBC-менеджер транзакций поверх
 * R2DBC-менеджера, и их становится два — выбор менеджера для
 * {@code @Transactional} становится неоднозначным, и запросы падают с
 * {@code NoUniqueBeanDefinitionException}. Транзакции в приложении
 * обслуживает {@code R2dbcTransactionManager}, и JDBC-менеджер здесь только
 * мешает.
 *
 * <p>Тема 4/11 «Реактивный доступ к данным в Spring Data R2DBC», раздел
 * «Основные отличия транзакций в R2DBC и JDBC»: на блокирующем стеке менеджер
 * транзакций создавался сам из единственного {@code DataSource}, поэтому
 * вопроса не возникало. Здесь соединений два — R2DBC и JDBC, — и
 * автоконфигурация не может знать, какой из них выбрать, отсюда и исключение.
 */
@SpringBootApplication(exclude = DataSourceTransactionManagerAutoConfiguration.class)
public class MyMarketAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyMarketAppApplication.class, args);
    }
}
