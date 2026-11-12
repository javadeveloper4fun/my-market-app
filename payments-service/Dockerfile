# Multi-stage сборка сервиса платежей.
#
# Контекст сборки — корень репозитория: сервис использует общий Gradle-проект
# и OpenAPI-контракт из api/. В runtime-образ устанавливается curl, чтобы
# docker compose мог проверять готовность сервиса через HTTP healthcheck.

FROM eclipse-temurin:25-jdk AS builder

WORKDIR /build

COPY gradle/ gradle/
COPY gradlew ./
COPY settings.gradle build.gradle gradle.properties ./
COPY config/ config/
COPY api/ api/
COPY market-app/ market-app/
COPY payments-service/ payments-service/

RUN chmod +x ./gradlew \
    && ./gradlew :payments-service:bootJar --no-daemon -Dorg.gradle.caching=true

FROM eclipse-temurin:25-jre

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /build/payments-service/build/libs/payments-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8081

USER 10001

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
