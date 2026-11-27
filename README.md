# my-market-app (Sprint 8)

Учебный проект «Витрина интернет-магазина» на Spring Boot 4 / WebFlux. Витрина и сервис платежей разделены на модули; взаимодействие между ними защищено OAuth2 (Keycloak, JWT/Client Credentials).

## Архитектура

- `market-app` (`:8080`) — витрина, карточка товара, корзина, заказы, загрузка изображений. Thymeleaf + Spring Data R2DBC + PostgreSQL + Redis + S3-compatible storage. Пользователи аутентифицируются form login; корзина/заказы изолированы по `userId`.
- `payments-service` (`:8081`) — RESTful сервис баланса/платежей по OpenAPI. OAuth2 Resource Server: принимает JWT от Keycloak, баланс/списание ведутся per-user по заголовку `X-User-Id`, который передаёт `market-app`.
- `keycloak` (`:8082`) — Authorization Server. Импортирует realm `market` из `docker/keycloak/realm-export.json`.
- `redis`, `market-postgres`, `market-s3mock`, `s3mock-seed` — инфраструктура витрины.

## Безопасность (Sprint 8)

- Анонимный пользователь: может смотреть витрину, карточку товара, статику, логин; не может работать с корзиной/заказами/покупкой.
- Аутентифицированный пользователь: имеет собственную корзину и заказы; доступ к чужим данным запрещён.
- `market-app -> payments-service`: OAuth2 Client Credentials (`ReactiveOAuth2AuthorizedClientManager` + WebClient filter), registration `market-app`, provider `keycloak`.
- `payments-service`: Spring Security OAuth2 Resource Server, проверка JWT, `/actuator/**` открыт для healthcheck.
- CSRF включён для web-форм; в шаблонах Thymeleaf используется hidden `_csrf`.

Демо-пользователи для входа в market-app (создаются seed-миграцией в БД, не в Keycloak): `user1/password`, `user2/password`.

## Запуск

Требования: Java 25 (toolchain), Docker.

```bash
docker compose up -d --build
```

Доступно:
- Витрина: <http://localhost:8080/items>
- Логин: <http://localhost:8080/login>
- Payments: <http://localhost:8081/api/v1/balance> (требует JWT + `X-User-Id`)
- Keycloak: <http://localhost:8082> (admin/admin)

Остановка: `docker compose down`. С удалением томов: `docker compose down -v`.

Запуск с prebuilt-образами (override заменяет Dockerfile’ы, но базовые сервисы берутся из основного compose, поэтому нужны оба файла):

```bash
./gradlew :market-app:bootJar :payments-service:bootJar
docker compose -f docker-compose.yml -f docker-compose.prebuilt.yml up -d --build
```

Зачем это нужно: обычные `market-app/Dockerfile` и `payments-service/Dockerfile` выполняют сборку Gradle внутри Docker — скачивают зависимости и компилируют проект, поэтому `docker compose up --build` занимает заметно больше времени. `Dockerfile.prebuilt` только копирует уже готовый JAR из `build/libs` в runtime-образ, поэтому сборка образа быстрее. Это удобно для локальной итерации, когда JAR уже собран на хосте, или в CI, где артефакт собирается отдельным шагом.

## Локальная разработка

Для локального запуска нужны PostgreSQL, Redis, S3Mock, Keycloak и payments-service. Проще поднять инфраструктуру через compose и запускать приложения из IDE/Gradle.

```bash
./gradlew :payments-service:bootRun
./gradlew :market-app:bootRun
```

## Тесты

```bash
./gradlew clean test
```

Точечно:

```bash
./gradlew :payments-service:test
./gradlew :market-app:test
```

## Конфигурация OAuth2

`market-app` читает:
- `MARKET_OAUTH2_CLIENT_ID` (default `market-app`)
- `MARKET_OAUTH2_CLIENT_SECRET` (default `market-secret`)
- `MARKET_OAUTH2_TOKEN_URI` (default локальный Keycloak)

`payments-service` читает:
- `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI`
- `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI`

В Docker внутренние адреса используют сервис `keycloak:8080`; с хоста Keycloak доступен на `localhost:8082`.

## API платежей

Контракт: `api/payments-api.yaml` (OpenAPI 3.0.3, source of truth). Клиент и сервер генерируются Gradle-задачами в `build/generated/...`.

- `GET /api/v1/balance` — баланс пользователя, требует JWT и `X-User-Id`.
- `POST /api/v1/payments` — идемпотентное списание суммы покупки, требует JWT, `X-User-Id` и `X-Attempt-Id` (UUID).

Коды: `200` успех, `400` некорректный запрос, `401` нет/невалидный JWT, `402` недостаточно средств, `403` доступ запрещён, `409` конфликт попытки: тот же `X-Attempt-Id` уже использован с другим пользователем или суммой.

## Примечания и упрощения

- Баланс и история попыток `payments-service` хранятся в памяти (`ConcurrentHashMap`) — учебное упрощение. Перезапуск `payments-service` возвращает баланс к `10000` и очищает историю `attemptId`: `docker compose restart payments-service`.
- Оформление в `market-app` защищено от двойного списания: попытка фиксируется в `checkout_attempts`, заказ создаётся до оплаты в статусе `AWAITING_PAYMENT`, при недоступности платежей заказ переводится в `PAYMENT_TIMEOUT`; повтор с тем же `attemptId` может перевести его в `PAID` без повторного списания. При отказе по insufficient funds заказ и попытка переводятся в `INSUFFICIENT_FUNDS`.
- `payments-service` проверяет JWT как OAuth2 Resource Server realm Keycloak, но не ограничивает вызовы только клиентом `market-app`: любой валидный JWT realm принимается. Для production нужно ограничивать `azp`/`client_id` или отдельный audience/scope.
- `X-User-Id` — доверенный заголовок, который выставляет `market-app`; payments-service не сверяет его с `sub`/claim JWT. Клиент с валидным JWT realm может подменить заголовок — для production нужен маппинг пользователя из токена.
- Redis используется как cache-aside для каталога; количество товара в корзине всегда читается из БД.
