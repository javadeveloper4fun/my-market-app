package ru.yandex.practicum.mymarket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Параметры S3-совместимого хранилища картинок.
 *
 * <p>Значения по умолчанию рассчитаны на локальный стенд: S3Mock из
 * {@code docker-compose.yml}, доступный по адресу {@code http://localhost:9090}.
 * В среде эксплуатации каждый параметр переопределяется переменной окружения,
 * поэтому в конфигурации не остаётся ничего, что нельзя подменить снаружи.
 *
 * Настраиваются следующие параметры:
 * {@code endpoint} — адрес хранилища; {@code bucket} — имя бакета;
 * {@code region} — регион; {@code accessKey} и {@code secretKey} — учётные данные;
 * {@code maxUploadSize} — предельный размер загружаемой картинки.
 */
@ConfigurationProperties(prefix = "market.image-s3")
public class ImageStorageProperties {

    /**
     * Предельный размер картинки по умолчанию — 5 МБ.
     */
    public static final String DEFAULT_MAX_UPLOAD_SIZE = "5MB";

    private String endpoint = "http://localhost:9090";

    private String bucket = "market-images";

    private String region = "us-east-1";

    private String accessKey = "market";

    private String secretKey = "market";

    private String maxUploadSize = DEFAULT_MAX_UPLOAD_SIZE;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getMaxUploadSize() {
        return maxUploadSize;
    }

    public void setMaxUploadSize(String maxUploadSize) {
        this.maxUploadSize = maxUploadSize;
    }
}
