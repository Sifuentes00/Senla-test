# Senla Test — сервис средних цен Binance

Spring Boot сервис, который раз в заданный интервал опрашивает публичное API Binance, сохраняет среднюю цену торговой пары в PostgreSQL и отдаёт собранную историю через REST API.

Стек: Java 24, Spring Boot 3.5, Spring Data JPA, PostgreSQL 18, Liquibase, Maven.

## Что умеет

- По расписанию (по умолчанию каждые 30 секунд) запрашивает `GET /api/v3/avgPrice` у Binance и сохраняет цену в БД.
- Хранит `symbol`, `price`, `fetchedAt` (момент записи) и `closeTime` (момент закрытия окна на Binance). Пары `(symbol, closeTime)` уникальны — повторный ответ с тем же окном не пишется.
- Один упавший тик опроса не останавливает расписание.

## Требования

- Docker Desktop (для запуска через compose и для интеграционных тестов)
- Либо JDK 24 + локальный PostgreSQL (для запуска без Docker)

## Переменные окружения

Все настройки задаются через переменные окружения. Файл `.env` в корне проекта не коммитится — создайте его сами:

```env
POSTGRES_DB=binance_prices
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres

DB_USER=binance_app
DB_PASSWORD=binance_secret
DB_URL=jdbc:postgresql://localhost:5432/binance_prices

BINANCE_BASE_URL=https://data-api.binance.vision
BINANCE_SYMBOL=BNBUSDT
BINANCE_POLL_INTERVAL=PT30S
```

| Переменная | Назначение | Пример |
|---|---|---|
| `POSTGRES_DB` | имя базы, создаётся контейнером postgres | `binance_prices` |
| `POSTGRES_USER` | суперпользователь контейнера postgres | `postgres` |
| `POSTGRES_PASSWORD` | пароль суперпользователя | `postgres` |
| `DB_USER` | логин приложения, создаётся initdb-скриптом | `binance_app` |
| `DB_PASSWORD` | его пароль | `binance_secret` |
| `DB_URL` | JDBC-строка подключения приложения | `jdbc:postgresql://localhost:5432/binance_prices` |
| `BINANCE_BASE_URL` | базовый URL Binance API | `https://data-api.binance.vision` |
| `BINANCE_SYMBOL` | торговая пара | `BNBUSDT` |
| `BINANCE_POLL_INTERVAL` | интервал опроса, ISO-8601 Duration | `PT30S`, `PT5M`, `PT1H` |

Нюансы:

- `DB_URL` нужен только для локального запуска. В docker-compose он переопределяется на внутренний хост `postgres:5432`.
- `POSTGRES_*` нужны только контейнеру postgres. При локальном запуске приложения без compose достаточно `DB_*` и `BINANCE_*`.

## Запуск через Docker Compose

Перед запуском создайте `.env` в корне проекта — шаблон со всеми переменными в разделе «Переменные окружения» выше.

```bash
docker compose up --build
```

Поднимутся два контейнера:

- `senla-postgres` — PostgreSQL 18. При первом старте выполняется `docker/postgres/initdb/01-init.sh`: создаётся пользователь `DB_USER` с правами на базу. Данные персистентны в volume `pgdata`.
- `senla-app` — приложение. Стартует после healthy-check postgres, Liquibase накатывает схему, начинается опрос Binance.

API доступен на `http://localhost:8080`.

Остановка:

```bash
docker compose down          # остановить
docker compose down -v       # остановить и удалить данные БД
```

## Локальный запуск (без Docker)

1. Поднимите PostgreSQL и создайте базу:

```sql
CREATE DATABASE binance_prices;
CREATE USER binance_app WITH PASSWORD 'binance_secret';
GRANT CONNECT ON DATABASE binance_prices TO binance_app;
GRANT USAGE, CREATE ON SCHEMA public TO binance_app;
```

2. Создайте `.env` в корне (шаблон выше) — библиотека spring-dotenv подхватит его автоматически, либо задайте переменные в run-конфигурации IDE.

3. Запустите:

```bash
./mvnw spring-boot:run
```

## Эндпоинты

База: `http://localhost:8080/api/v1/prices`. Все методы — `GET`. Параметры `from`/`to` — ISO-8601 Instant в UTC.

### Последняя цена

```bash
curl http://localhost:8080/api/v1/prices/latest
```

```json
{"symbol":"BNBUSDT","price":631.65416704,"fetchedAt":"2026-11-13T07:52:02.191850Z"}
```

Если записей ещё нет — `404 {"status":404,"message":"No price records yet",...}`.

### Средняя цена за диапазон

```bash
curl "http://localhost:8080/api/v1/prices/average?from=2026-11-13T07:00:00Z&to=2026-11-13T08:00:00Z"
```

```json
{"averagePrice":631.72}
```

Среднее считается агрегацией `AVG` на стороне БД по полю `fetchedAt`. Пустой диапазон — `404`.

### Список записей с пагинацией

```bash
curl "http://localhost:8080/api/v1/prices?from=2026-11-13T07:00:00Z&to=2026-11-13T09:00:00Z&page=0&size=10"
```

```json
{
  "items": [{"symbol":"BNBUSDT","price":631.81,"fetchedAt":"..."}],
  "page": 0,
  "size": 10,
  "totalElements": 240,
  "hasNext": true
}
```

Стандартные параметры Spring-страниц: `page` (с 0), `size`, `sort`.

### Тренд за диапазон

```bash
curl "http://localhost:8080/api/v1/prices/trend?from=2026-11-13T07:00:00Z&to=2026-11-13T08:00:00Z"
```

```json
{"symbol":"BNBUSDT","startDate":"2026-11-13T07:00:00Z","endDate":"2026-11-13T08:00:00Z","trend":"UPWARD"}
```

Сравнивается первая и последняя цена диапазона. Изменение меньше 0.1% считается `FLAT`, иначе `UPWARD`/`DOWNWARD`.

### Ошибки

Единый формат ошибки от `GlobalExceptionHandler`:

```json
{"status":400,"message":"'from' must be before 'to'","timestamp":"..."}
```

- `400` — `from` не раньше `to`, кривой формат даты, отсутствует обязательный параметр.
- `404` — нет данных в диапазоне / нет записей вообще.
- `500` — прочее.

## Тесты

```bash
./mvnw test                        # всё (нужен Docker для интеграционного теста)
./mvnw test -Dtest=PriceServiceTest  # только unit-тесты, без Docker
./mvnw test -Dtest=PriceServiceIT    # только интеграционный, нужен Docker
```

- `PriceServiceTest` — unit-тесты `PriceService` на Mockito: сохранение и дедупликация при опросе, проброс ошибок Binance, все четыре метода чтения, исключения на пустых и инвертированных диапазонах, классификация тренда.
- `PriceServiceIT` — интеграционный тест на Testcontainers: поднимает PostgreSQL 18 в контейнере, накатывает схему Liquibase и проверяет все четыре эндпоинта через HTTP, включая пагинацию и ответы ошибок.
