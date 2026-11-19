# Multi-stage сборка витрины интернет-магазина.
#
# Контекст сборки — корень репозитория, потому что market-app зависит от
# общего Gradle-проекта, OpenAPI-контракта в api/ и настроек линтеров в
# config/. JAR собирается внутри образа, поэтому после git clone достаточно
# выполнить docker compose up -d --build.

FROM eclipse-temurin:25-jdk AS builder

WORKDIR /build

COPY gradle/ gradle/
COPY gradlew ./
COPY settings.gradle build.gradle gradle.properties ./
COPY config/ config/
COPY api/ api/
COPY market-app/ market-app/
COPY payments-service/ payments-service/

RUN ./gradlew :market-app:bootJar --no-daemon -Dorg.gradle.caching=true

FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=builder /build/market-app/build/libs/market-app-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

USER 10001

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
