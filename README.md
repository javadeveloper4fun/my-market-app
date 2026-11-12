# my-market-app (Спринт 7)

Витрина интернет-магазина на Spring Boot 4 / WebFlux (`Thymeleaf` + `Spring Data R2DBC` + `PostgreSQL`) и отдельный реактивный сервис платежей. Проект собран как Gradle-мультипроект.

## Архитектура

- `market-app` (порт `8080`) — витрина, карточка товара, корзина, оформление заказа. Ходит в PostgreSQL (R2DBC), S3Mock (картинки), Redis (кеш каталога) и в сервис платежей по HTTP.
- `payments-service` (порт `8081`) — RESTful-сервис баланса/платежей по контракту OpenAPI. Хранит баланс в памяти процесса (`AtomicLong`), без собственной БД.
- `redis` — кеш метаданных карточек и страниц каталога (количество товара в корзине в кеш не кладётся).
- `market-postgres`, `market-s3mock`, `s3mock-seed` — инфраструктура витрины.

Сценарий покупки: `prepare` заказа (чтение корзины/снимок) → `POST /api/v1/payments` → сохранение заказа и очистка только купленных позиций корзины. При `402`/`503`/недоступности платежей заказ не создаётся, корзина не очищается, пользователь возвращается в корзину с сообщением.

## Структура модулей

```text
.
├── api/payments-api.yaml        # OpenAPI-контракт сервиса платежей (source of truth)
├── config/checkstyle/           # общий Checkstyle
├── market-app/                  # витрина (WebFlux, Thymeleaf, R2DBC, Redis, платежный клиент)
├── payments-service/            # сервис платежей (WebFlux, OpenAPI server interfaces)
├── docker-compose.yml
└── build.gradle, settings.gradle, gradle.properties
```

Сгенерированный код (`build/generated/...`) не коммитится и исключён из Checkstyle/Spotless.

## Требования

- Java 25 (Gradle toolchain)
- Docker (Testcontainers в тестах и полный запуск стенда)
- Gradle Wrapper (`gradlew.bat` / `./gradlew`)

## Сборка и тесты

Полная сборка (линтеры + unit/slice/integration тесты):

```bash
./gradlew clean build
```

Отдельно по модулям:

```bash
./gradlew :payments-service:test
./gradlew :market-app:test
```

Тесты `market-app` используют Testcontainers (PostgreSQL, Redis) и встроенную H2 там, где это достаточно, поэтому для полного прогона нужен запущенный Docker.

## Запуск

### Docker Compose (рекомендуется)

```bash
docker compose up -d --build
```

Доступно:
- витрина: <http://localhost:8080/items>
- сервис платежей: <http://localhost:8081/api/v1/balance>

Остановка: `docker compose down` (с данными: `docker compose down -v`).

### Локально

Нужны PostgreSQL, Redis, S3Mock и `payments-service` (можно поднять инфраструктуру через compose, а сервисы запускать из IDE/Gradle). Переменные по умолчанию в `market-app/src/main/resources/application.yml` рассчитаны на локальные порты compose (`localhost:5439` для БД, `localhost:6379` для Redis, `localhost:9090` для S3, `localhost:8081` для платежей).

```bash
./gradlew :payments-service:bootRun
./gradlew :market-app:bootRun
```

## API сервиса платежей

Контракт: `api/payments-api.yaml` (OpenAPI 3.0.3). `payments-service` реализует сгенерированные серверные интерфейсы, `market-app` использует сгенерированный WebClient-клиент.

### `GET /api/v1/balance`
Ответ `200`:
```json
{ "balance": 10000 }
```

### `POST /api/v1/payments`
Запрос:
```json
{ "amount": 1500 }
```

Ответы:
- `200` — списание выполнено: `{ "amount": 1500, "balance": 8500 }`
- `400` — некорректная сумма: `{ "code": "INVALID_AMOUNT", "message": "..." }`
- `402` — недостаточно средств: `{ "code": "INSUFFICIENT_FUNDS", "message": "..." }`
- `503` — временная недоступность: `{ "code": "SERVICE_UNAVAILABLE", "message": "..." }`

Примеры:
```bash
curl http://localhost:8081/api/v1/balance
curl -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"amount":1500}'
```

## Redis-кеш (`market-app`)

- Реализация cache-aside на `ReactiveStringRedisTemplate` + JSON (`ObjectMapper`), без `@Cacheable`/`RedisCacheManager` (нужен контроль над реактивным потоком и ошибками).
- Ключи: `market:item:{id}`, `market:items:{version}`, страницы `market:items:{version}:{search}:{sort}:{page}:{size}`.
- TTL настраивается (`market.cache.items.ttl`, по умолчанию `PT5M`).
- Инвалидация: удаление карточки и инкремент версии каталога при изменении товара; массовый `KEYS/SCAN` не используется.
- `count` (количество в корзине) **всегда** читается из БД (`cart_item`), в кеш кладутся только метаданные товара/страницы.
- При ошибке Redis кеш работает fail-open: чтение деградирует в промах, запись/инвалидация не ломают запрос (данные берутся из БД).

## OpenAPI и генерация кода

- Спецификация: `api/payments-api.yaml`.
- Генерация запускается задачами Gradle (`generatePaymentsServer` / `generatePaymentsClient` + патч клиента) и подключена к компиляции.
- Сгенерированные источники лежат в `build/` модулей и не редактируются вручную.

## Ограничения

- Баланс платежей хранится в памяти и сбрасывается при перезапуске `payments-service` (начальное значение `PAYMENTS_INITIAL_BALANCE`, по умолчанию 10000).
- Компенсация/`refund` и идемпотентность платежей не реализованы: если списание прошло, а сохранение заказа упало, состояние фиксируется как требующая разбора ситуация (см. логи), автоматического отката нет.
- `count` в корзине не кешируется специально, чтобы не расходиться с БД; кеш каталога может быть кратковременно stale до TTL/инвалидации.
- Один пользовательский сценарий (без авторизации/раздельных корзин), как и в предыдущем спринте.

## Troubleshooting

- **Redis недоступен** — витрина продолжает работать, читая из БД (fail-open); проверьте `SPRING_DATA_REDIS_HOST/PORT` и `docker compose ps`.
- **payments-service недоступен** — баланс/покупка недоступны, корзина показывает сообщение о недоступности платежей; заказ не создаётся.
- **402 при покупке** — недостаточно средств, корзина сохраняется.
- **Docker healthcheck payments-service** — образ должен содержать `curl` (используется в compose).
- **Сборка OpenAPI/Gradle** — при проблемах с кэшем конфигурации Gradle очистите `.gradle`/`build` и пересоберите; сгенерированный клиент может давать deprecation warnings от зависимостей генератора.
