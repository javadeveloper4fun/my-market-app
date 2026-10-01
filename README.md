# my-market-app

Спринт 5: витрина интернет-магазина на Spring Boot (`Web MVC` + `Thymeleaf` + `Spring Data JPA` + `Hibernate` + `PostgreSQL`).

## Возможности

- витрина товаров с поиском по названию/описанию, сортировкой (по алфавиту и цене) и пагинацией;
- карточка товара;
- корзина: изменение количества товара, удаление из корзины;
- оформление заказа и список заказов;
- HTML-шаблоны взяты из готового макета спринта, контракт эндпоинтов соответствует постановке (подробнее ниже).

## Требования

- Java 21;
- Docker (для базы данных PostgreSQL и запуска приложения в контейнере);
- Gradle (не обязателен — в проекте есть Gradle Wrapper).

## Сборка и тесты

```bash
./gradlew build
```

Команда собирает проект, прогоняет линтеры (checkstyle, spotless) и интеграционные тесты (JUnit 5, Spring Boot Test, MockMvc, DataJpaTest). Готовый Executable JAR лежит в `build/libs/my-market-app-0.0.1-SNAPSHOT.jar`.

## Запуск локально (в среде разработки)

1. Поднять базу данных:

   ```bash
   docker compose up -d market-postgres
   ```

2. Запустить приложение:

   ```bash
   ./gradlew bootRun
   ```

   Либо собрать JAR и запустить его:

   ```bash
   ./gradlew bootJar
   java -jar build/libs/my-market-app-0.0.1-SNAPSHOT.jar
   ```

3. Открыть витрину в браузере: <http://localhost:8080/items>.

По умолчанию приложение подключается к PostgreSQL по адресу `localhost:5439` (база `market`, пользователь/пароль `market`). Параметры подключения можно переопределить переменными окружения `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.

## Запуск в Docker

Образ приложения собирается из Executable JAR (Gradle в образ не устанавливается), поэтому сначала соберите JAR:

```bash
./gradlew bootJar
```

Затем поднимите весь стек — приложение и базу данных:

```bash
docker compose up -d --build
```

Приложение станет доступно по адресу <http://localhost:8080/items>, база данных — по адресу `localhost:5439` (порт проброшен для локального доступа).

Остановить:

```bash
docker compose down
```

Запустить без Docker Compose можно так:

```bash
docker build -t my-market-app .
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/market \
  my-market-app
```

## Эндпоинты

Контракт эндпоинтов соответствует постановке спринта 5:

- `GET /` и `GET /items` — витрина (шаблон `items`), параметры: `search`, `sort` (`NO`/`ALPHA`/`PRICE`), `pageNumber`, `pageSize`;
- `POST /items` — изменение количества в корзине со страницы витрины: редирект на `/items` с сохранением параметров запроса;
- `GET /items/{id}` и `POST /items/{id}` — карточка товара (шаблон `item`);
- `GET /cart/items` и `POST /cart/items` — корзина (шаблон `cart` с атрибутами `items` и `total`), возможные действия: `MINUS`, `PLUS`, `DELETE`;
- `GET /orders` — список заказов (шаблон `orders`);
- `GET /orders/{id}` — страница заказа (шаблон `order`, флаг `newOrder`);
- `POST /buy` — оформление заказа: редирект на `/orders/{id}?newOrder=true`.

`POST /items` и `POST /buy` возвращают редирект, как требует постановка; `POST /items/{id}` и `POST /cart/items` возвращают шаблон напрямую.

## Структура проекта

```
src/main/java/ru/yandex/practicum/mymarket/
├── controller/   # WebMVC-контроллеры
├── dto/          # DTO для отображения на страницах
├── model/        # JPA-сущности
├── repository/   # Spring Data JPA-репозитории
└── service/      # сервисный слой
```