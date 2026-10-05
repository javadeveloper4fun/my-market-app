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
- Docker — нужен для запуска PostgreSQL и самого приложения в контейнере, а также для части тестов (см. «Сборка и тесты»);
- Gradle (не обязателен — в проекте есть Gradle Wrapper).

## Сборка и тесты

```bash
./gradlew build
```

Команда собирает проект, прогоняет линтеры (checkstyle, spotless) и тесты (JUnit 5, Spring Boot Test, MockMvc, DataJpaTest). Готовый Executable JAR лежит в `build/libs/my-market-app-0.0.1-SNAPSHOT.jar`.

Большая часть тестов работает на встроенной H2 в режиме `MODE=PostgreSQL`, и схему для них создаёт тот же changelog, что и основное приложение. Но `ItemRepositoryPostgreSqlTest` поднимает настоящий PostgreSQL в контейнере через Testcontainers, поэтому **для `./gradlew build` Docker необходим**. Тест запускает `postgres:17.5` — тот же образ, что и в `docker-compose.yml`, — и применяет тот же changelog, что гарантирует переносимость миграций на боевую СУБД.

Без Docker пройдут только тесты на H2:

```bash
./gradlew test --tests '*RepositoryTest' --tests '*ServiceTest' --tests '*ControllerTest' --tests '*MvcFlowIntegrationTest'
```

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

Схему приложения описывает набор файлов миграций: корневой
[`db.changelog-master.yaml`](src/main/resources/db/changelog/db.changelog-master.yaml) подключает
остальные через `include` — по одному файлу на домен
(`items`, `cart_item`, `orders`, `order_item`, внешние ключи, seed-данные, индексы) в каталоге
`db/changelog/changes/`. При старте приложение сверяет содержимое changelog с таблицей `DATABASECHANGELOG` и выполняет недостающие changeset'ы.

У каждого changeset объявлены `preConditions` и `rollback`. `preConditions` по умолчанию работают
в режиме `HALT`: если объект, который changeset собирается создать, уже существует, changeset
не выполняется, а приложение падает при старте с `PreconditionFailedException`. Режим
`MARK_RAN` намеренно не используется — он отметил бы изменение выполненным, хотя оно не
выполнялось, и база прошла бы миграции, не будучи ими создана.

Итого после `docker compose up -d --build` ревьюер получает готовую к работе витрину: базу, пользователя, все таблицы, внешние ключи, индекс и 4 товара из seed-данных. Ни одной промежуточной команды не требуется.

**Повторные запуски безопасны.** Liquibase хранит историю применённых changeset'ов в `DATABASECHANGELOG`, поэтому при каждом следующем старте уже применённые изменения пропускаются, а товары не дублируются.

Отдельно стоит отметить, чем это отличается от прежней схемы `hbm2ddl=create`: тот режим пересоздавал таблицы при каждом старте и уничтожал накопленные данные, не оставляя никакой истории. Liquibase этого не делает.

### Если приложение падает при старте с `PreconditionFailedException`

Это означает, что база была создана **не** через Liquibase (например, ранее через `hbm2ddl=create`): таблицы в ней уже есть, а таблица `DATABASECHANGELOG` — нет. Liquibase видит пустую историю, и `preConditions` первого changeset'а обнаруживают, что таблица `items` уже создана.

Ошибка специально не скрывается и не помечается как «выполнено»: приложение останавливается, чтобы расхождение схемы пришлось разобрать явно. До появления `preConditions` на этом же месте падало `ERROR: relation "items" already exists` — теперь падение происходит раньше и с более понятной причиной.

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