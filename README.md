# my-market-app (Спринт 7)

Витрина интернет-магазина на Spring Boot 4 / WebFlux (`Thymeleaf` + `Spring Data R2DBC` + `PostgreSQL`) и отдельный реактивный сервис платежей. Проект собран как Gradle-мультипроект.

## Архитектура

- `market-app` (порт `8080`) — витрина, карточка товара, корзина, оформление заказа. Ходит в PostgreSQL (R2DBC), S3Mock (картинки), Redis (кеш каталога) и в сервис платежей по HTTP.
- `payments-service` (порт `8081`) — RESTful-сервис баланса/платежей по контракту OpenAPI. Хранит баланс в памяти процесса (`AtomicLong`), без собственной БД.
- `redis` — кеш метаданных карточек и страниц каталога (количество товара в корзине в кеш не кладётся).
- `market-postgres`, `market-s3mock`, `s3mock-seed` — инфраструктура витрины.

Сценарий покупки: `prepare` заказа (чтение корзины/снимок) → `POST /api/v1/payments` → сохранение заказа и очистка только купленных позиций корзины. При `402` или недоступности платежей заказ не создаётся, корзина не очищается, пользователь возвращается в корзину с сообщением.

## Структура модулей

```text
.
├── api/payments-api.yaml        # OpenAPI-контракт сервиса платежей (source of truth)
├── config/checkstyle/           # общий Checkstyle
├── market-app/                  # витрина (WebFlux, Thymeleaf, R2DBC, Redis, платежный клиент)
├── payments-service/            # сервис платежей (WebFlux, OpenAPI server interfaces)
├── scripts/                     # скрипты проверки и smoke-теста
├── docker-compose.yml
├── docker-compose.prebuilt.yml  # запуск из заранее собранных bootJar (fallback)
└── build.gradle, settings.gradle, gradle.properties
```

Сгенерированный код (`build/generated/...`) не коммитится и исключён из Checkstyle/Spotless.

## Требования

- Java 25 (Gradle toolchain)
- Docker (Testcontainers в тестах и полный запуск стенда)
- Gradle Wrapper (`gradlew.bat` / `./gradlew`)

## Сборка и тесты

Полная сборка (линтеры + unit/slice/integration тесты), нужен запущенный Docker для Testcontainers в `market-app`:

```bash
./gradlew clean build
```

По модулям (полный цикл с проверками):

```bash
./gradlew :payments-service:clean :payments-service:build
./gradlew :market-app:clean :market-app:build
```

В средах с ограниченной памятью можно запускать с одним воркером и без демона:

```bash
./gradlew --no-daemon --max-workers=1 :payments-service:clean :payments-service:build
./gradlew --no-daemon --max-workers=1 :market-app:clean :market-app:build
```

Для Windows есть скрипт проверки/подъёма стенда: `scripts/verify-and-smoke.ps1` (PowerShell 7+, Docker, `curl.exe`).

## Запуск

### Docker Compose (рекомендуется, из клона)

`docker-compose.yml` использует multi-stage Dockerfile’ы модулей: после `git clone` достаточно Docker и сети для загрузки зависимостей Gradle/базовых образов.

```bash
docker compose up -d --build
```

Доступно:
- витрина: <http://localhost:8080/items>
- сервис платежей: <http://localhost:8081/api/v1/balance>

Остановка: `docker compose down` (с сохранением томов). Удаление данных: `docker compose down -v`.

### Docker Compose из заранее собранных jar (fallback)

Если при сборке образов внутри Docker недоступны репозитории Gradle/зависимостей, можно собрать bootJar на хосте и использовать prebuilt-образы:

```bash
./gradlew :payments-service:bootJar :market-app:bootJar
docker compose -f docker-compose.yml -f docker-compose.prebuilt.yml up -d --build
```

PowerShell (Windows): `.\scripts\verify-and-smoke.ps1` (сборка + `docker compose up -d --build` + базовые smoke-проверки) или `.\scripts\verify-and-smoke.ps1 -SkipBuild` с `-f docker-compose.prebuilt.yml` при ручном запуске после сборки jar.

### Локально

Нужны PostgreSQL, Redis, S3Mock и `payments-service` (можно поднять инфраструктуру через compose, а сервисы запускать из IDE/Gradle). Переменные по умолчанию в `market-app/src/main/resources/application.yml` рассчитаны на локальные порты compose (`localhost:5439` для БД, `localhost:6379` для Redis, `localhost:9090` для S3, `localhost:8081` для платежей).

```bash
./gradlew :payments-service:bootRun
./gradlew :market-app:bootRun
```

### Smoke-проверка

```bash
curl http://localhost:8081/api/v1/balance
curl -I http://localhost:8080/items
```

Дополнительно вручную: сценарий покупки (успех; `402` при недостатке средств — заказ не создаётся, корзина сохраняется; при недоступном `payments-service` — заказ не создаётся, показывается ошибка оплаты).

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
- `400` — некорректный запрос: `{ "code": "INVALID_AMOUNT" | "INVALID_REQUEST", "message": "..." }`
- `402` — недостаточно средств: `{ "code": "INSUFFICIENT_FUNDS", "message": "..." }`

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
- TTL настраивается (`market.cache.items.ttl`, по умолчанию `PT5M`). Кеш можно отключить флагом `market.cache.enabled=false` (в большинстве тестов используется `NoOpItemCache`).
- Версия каталога читается один раз на запрос страницы и используется в одном и том же ключе для чтения/записи. Поиск для ключа кеша нормализуется (trim, нижний регистр), сортировка приводится к каноническому значению (`NO`/`ALPHA`/`PRICE`), размер страницы ограничивается безопасным максимумом (100).
- Инвалидация каталога: инкремент версии страниц выполняется после успешного завершения транзакции сохранения товара (`createItem`/`saveWithImage`); массовый `KEYS/SCAN` не используется. Отдельный метод инвалидации карточки в production-сценариях изменения/удаления товара сейчас не вызывается (таких эндпоинтов нет).
- `count` (количество в корзине) **всегда** читается из БД (`cart_item`), в кеш кладутся только метаданные товара/страницы.
- При ошибке Redis или таймауте кеш работает fail-open: чтение деградирует в промах, запись/инвалидация не ломают запрос (данные берутся из БД). Для быстрого fail-open настроены короткие таймауты Redis (`1s`).

## OpenAPI и генерация кода

- Спецификация: `api/payments-api.yaml`.
- Генерация запускается задачами Gradle (`generatePaymentsServer` / `generatePaymentsClient` + патч клиента) и подключена к компиляции.
- Сгенерированные источники лежат в `build/` модулей и не редактируются вручную.

## Ограничения

- Баланс платежей хранится в памяти и сбрасывается при перезапуске `payments-service` (начальное значение `PAYMENTS_INITIAL_BALANCE`, по умолчанию 10000).
- Компенсация/`refund` и идемпотентность платежей не реализованы: если списание прошло, а сохранение заказа упало, состояние требует разбора; в логах фиксируется ошибка с маркером `PAYMENT_SUCCEEDED_ORDER_FAILED` (сумма и состав заказа), автоматического отката нет.
- `count` в корзине не кешируется специально, чтобы не расходиться с БД; кеш каталога может быть кратковременно stale до TTL/инвалидации.
- Один пользовательский сценарий (без авторизации/раздельных корзин), как и в предыдущем спринте.
- В Docker Compose для Redis задан лимит памяти `256mb` и политика `volatile-lru`, чтобы ключ версии каталога без TTL не вытеснялся.
