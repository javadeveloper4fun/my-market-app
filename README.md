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

Демо-пользователи (seed миграция market-app): `user1/password`, `user2/password`.

## Запуск

Требования: Java 21+ (toolchain), Docker.

```bash
docker compose up -d --build
```

Доступно:
- Витрина: <http://localhost:8080/items>
- Логин: <http://localhost:8080/login>
- Payments: <http://localhost:8081/api/v1/balance> (требует JWT + `X-User-Id`)
- Keycloak: <http://localhost:8082> (admin/admin)

Остановка: `docker compose down`. С удалением томов: `docker compose down -v`.

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
- `POST /api/v1/payments` — списание, требует JWT и `X-User-Id`.

Коды: `200` успех, `400` некорректный запрос, `401` нет/невалидный JWT, `402` недостаточно средств.

## Примечания

- Баланс payments-service хранится в памяти (`AtomicLong` per user) — учебное упрощение.
- Redis используется как cache-aside для каталога; количество товара в корзине всегда читается из БД.
