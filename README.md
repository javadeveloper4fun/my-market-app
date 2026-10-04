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
- Docker и Docker Compose — только для запуска PostgreSQL и самого приложения в контейнере; для сборки и тестов Docker не нужен;
- Gradle (не обязателен — в проекте есть Gradle Wrapper).

## Сборка и тесты

```bash
./gradlew build
```

Команда собирает проект, прогоняет линтеры (checkstyle, spotless) и интеграционные тесты (JUnit 5, Spring Boot Test, MockMvc, DataJpaTest). Готовый Executable JAR лежит в `build/libs/my-market-app-0.0.1-SNAPSHOT.jar`.

Docker для этого не нужен: тесты работают на встроенной H2 в режиме `MODE=PostgreSQL`, и схему для них создаёт тот же changelog, что и основное приложение. То есть `./gradlew build` проверяет проект на машине без Docker вообще.

## Как создаётся база данных

Никаких SQL-скриптов вручную запускать не нужно. База появляется сама, причём в два независимых шага — их стоит различать, потому что отвечают за разное.

**Шаг 1 — сама база и пользователь — создаёт Docker.**

В `docker-compose.yml` у сервиса `market-postgres` заданы переменные окружения `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`. Их читает entrypoint-скрипт официального образа `postgres` при первом старте контейнера, когда том пуст.

```yaml
POSTGRES_DB: market
POSTGRES_USER: market
POSTGRES_PASSWORD: market
```

После `docker compose up -d market-postgres` база `market` и пользователь `market` существуют, но в ней **ноль таблиц**.

**Шаг 2 — таблицы, индексы и данные — создаёт Liquibase при старте приложения.**

Схему приложения описывает единственный файл [`src/main/resources/db/changelog/db.changelog-master.yaml`](src/main/resources/db/changelog/db.changelog-master.yaml). При старте приложение сверяет содержимое changelog с таблицей `DATABASECHANGELOG` и выполняет недостающие changeset'ы.

Итого после `docker compose up -d --build` ревьюер получает готовую к работе витрину: базу, пользователя, все таблицы, внешние ключи, индекс и 4 товара из seed-данных. Ни одной промежуточной команды не требуется.

**Повторные запуски безопасны.** Liquibase хранит историю применённых changeset'ов в `DATABASECHANGELOG`, поэтому при каждом следующем старте уже применённые изменения пропускаются, а товары не дублируются.

Отдельно стоит отметить, чем это отличается от прежней схемы `hbm2ddl=create`: тот режим пересоздавал таблицы при каждом старте и уничтожал накопленные данные, не оставляя никакой истории. Liquibase этого не делает.

### Если возникает ошибка `relation "..." already exists`

Она означает, что база была создана **не** через Liquibase (например, ранее через `hbm2ddl=create`), поэтому таблицы в ней уже есть, а таблицы `DATABASECHANGELOG` — нет. Liquibase видит пустую историю и пытается выполнить создание таблиц заново.

Самый быстрый способ для учебного проекта — начать с чистой базы (данные будут потеряны):

```bash
docker compose down -v
docker compose up -d --build
```

Для существующей базы с нужными данными этот способ не подходит: тогда требуется разовая процедура baseline — одна отметка текущего состояния как уже применённого. Она в рамках спринта не рассматривается.

## Запуск локально (в среде разработности)

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

Образ собирается в две стадии: в стадии `builder` Gradle из локального wrapper собирает Executable JAR, в стадии `runtime` остаётся только JRE 21 и готовый JAR. Gradle в итоговый образ не попадает.

Отдельная подготовка на хосте не нужна — достаточно:

```bash
docker compose up -d --build
```

Команда соберёт JAR внутри образа, создаст пустую базу данных и запустит приложение, которое само применит миграции Liquibase и наполнит витрину товарами.

Приложение станет доступно по адресу <http://localhost:8080/items>, база данных — по адресу `localhost:5439` (порт проброшен для локального доступа).

Остановить:

```bash
docker compose down
```

Полностью удалить и базу данных, и её данные (данные будут потеряны):

```bash
docker compose down -v
```

Запустить без Docker Compose можно так:

```bash
docker build -t my-market-app .
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/market \
  -e SPRING_DATASOURCE_USERNAME=market \
  -e SPRING_DATASOURCE_PASSWORD=market \
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