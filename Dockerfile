# ===================================================================
# Dockerfile приложения «Витрина интернет-магазина».
#
# Образ собирается из Executable JAR и запускает его во встроенном
# контейнере сервлетов (Tomcat) на порту 8080.
#
# JAR собирается локально (Gradle в образ не устанавливается):
#   ./gradlew bootJar
# подробнее — в README.md, раздел «Запуск в Docker».
# ===================================================================

FROM eclipse-temurin:21-jre
WORKDIR /app

# Executable JAR со встроенными зависимостями (Spring Boot fat jar).
COPY build/libs/my-market-app-0.0.1-SNAPSHOT.jar app.jar

# Открываемый порт веб-приложения.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]