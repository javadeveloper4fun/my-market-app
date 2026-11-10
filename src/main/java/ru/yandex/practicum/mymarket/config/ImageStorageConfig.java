package ru.yandex.practicum.mymarket.config;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Асинхронный клиент S3 для хранения картинок товаров.
 *
 * <p>Клиент подключён напрямую, без Spring Cloud AWS. Управление зависимостями
 * такого решения взяло бы на себя версию Boot 3.x, а проект живёт на Boot 4.1.1,
 * и связка перестала бы собираться. Собственный бин в этом смысле честнее: видно,
 * что именно настроено, и ничего лишнего не подтягивается.
 *
 * <p>Регион у S3Mock значения не имеет, но у настоящего S3 он обязателен, и без
 * него клиент отказывается строить запрос. Поэтому задан регион по умолчанию.
 *
 * <p>Учётные данные — статические. Это осознанный выбор для учебного проекта и
 * для локального хранилища, которое проверок не делает: переменные окружения
 * перекрывают их для настоящего S3. В среде эксплуатации статические ключи в
 * конфигурации быть не должны — там нужен поставщик из IAM-роли или из менеджера
 * секретов.
 *
 * <p>{@code forcePathStyle} обязателен: настоящий S3 работает с виртуальными
 * хостами бакета вида {@code bucket.s3.amazonaws.com}, а S3Mock так не умеет и
 * ждёт бакет в начале пути. Без этого флага клиент строит адрес, который
 * локальное хранилище не понимает.
 */
@Configuration
@EnableConfigurationProperties(ImageStorageProperties.class)
public class ImageStorageConfig {

    /**
     * Создаёт асинхронный клиент S3.
     *
     * @param properties параметры хранилища
     * @return клиент S3 поверх HTTP-клиента на Netty
     */
    @Bean
    public S3AsyncClient s3AsyncClient(ImageStorageProperties properties) {
        return S3AsyncClient.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }
}
