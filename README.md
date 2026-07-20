# FinCore API

A production-grade banking/fintech REST API built with **Java 21** and **Spring Boot 3.2**. It implements user onboarding, JWT authentication with refresh-token rotation, account management, and safe concurrent money movement — the same patterns used in real core-banking integrations.

## Architecture

```
Controller  →  Service (interface + impl)  →  Repository  →  PostgreSQL
    │                 │
   DTOs          Domain entities
 (records)      (JPA + optimistic locking)
```

Layered architecture with strict separation: controllers never touch entities directly, MapStruct handles entity↔DTO mapping at compile time, and Flyway owns the schema (Hibernate runs in `validate` mode).

## Key features

**Security**
- Stateless JWT authentication (access token 15 min, refresh token 7 days)
- Refresh-token **rotation** — every refresh revokes the old token and issues a new pair; tokens are stored as SHA-256 hashes, never in plaintext
- BCrypt (cost 12) password hashing, password complexity validation
- Account lockout after 5 failed logins (15-minute lock)
- Per-IP token-bucket rate limiting (Bucket4j), CORS allowlist, CSP and frame-deny headers
- Role-based access (`@PreAuthorize`) — deposits are admin-only

**Money movement done correctly**
- `BigDecimal(19,4)` everywhere — never floating point for money
- **Pessimistic row locks** (`SELECT ... FOR UPDATE`) on both accounts during a transfer, acquired in deterministic order to prevent deadlocks
- `@Version` optimistic locking as a second guard
- **Idempotency** via the `Idempotency-Key` header — retried requests replay the original result instead of double-debiting
- DB-level `CHECK` constraints (non-negative balance, positive amount)

**Operations**
- Correlation IDs on every request (`X-Correlation-Id`), propagated into logs via MDC
- Actuator health/metrics/prometheus endpoints
- Global exception handler — consistent JSON envelope, no stack-trace leakage
- Caffeine caching, HikariCP tuning, Hibernate batch inserts

## API surface

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/auth/register` | Public | Create a user |
| POST | `/api/v1/auth/login` | Public | Get access + refresh tokens |
| POST | `/api/v1/auth/refresh` | Public | Rotate refresh token |
| POST | `/api/v1/auth/logout` | Bearer | Revoke all refresh tokens |
| POST | `/api/v1/accounts` | Bearer | Open an account (NUBAN-style number) |
| GET | `/api/v1/accounts` | Bearer | List my accounts (paginated) |
| GET | `/api/v1/accounts/{accountNumber}` | Bearer | Account details |
| POST | `/api/v1/transactions/transfer` | Bearer | Transfer (supports `Idempotency-Key`) |
| POST | `/api/v1/transactions/deposit` | Admin | Credit an account |
| GET | `/api/v1/transactions/{reference}` | Bearer | Transaction by reference |
| GET | `/api/v1/transactions/account/{accountNumber}` | Bearer | Account history (paginated) |

Interactive docs: `http://localhost:8080/swagger-ui.html`

## Quick start

### With Docker (recommended)
```bash
docker compose up --build
```
API on `http://localhost:8080`, PostgreSQL on `5432`. Flyway migrates automatically.

### Local development
```bash
# 1. Start PostgreSQL only
docker compose up postgres -d

# 2. Run the app
./mvnw spring-boot:run
```

### Run tests
```bash
./mvnw test
```
Unit tests (Mockito) cover the transfer engine; integration tests (`MockMvc` + H2) cover the full auth flow.

## Example flow

```bash
# Register
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"amos@example.com","password":"Str0ngPass1","firstName":"Amos","lastName":"Adebisi","phoneNumber":"+2348012345678"}'

# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"amos@example.com","password":"Str0ngPass1"}'

# Open an account (use the accessToken from login)
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"accountType":"SAVINGS","currency":"NGN"}'

# Transfer with idempotency protection
curl -X POST http://localhost:8080/api/v1/transactions/transfer \
  -H "Authorization: Bearer <accessToken>" \
  -H "Idempotency-Key: 7f3c9a1e-unique-per-attempt" \
  -H "Content-Type: application/json" \
  -d '{"sourceAccountNumber":"1234567890","destinationAccountNumber":"9876543210","amount":5000.00,"narration":"Rent"}'
```

## Configuration

All secrets are environment-driven (see `.env.example`). The bundled `JWT_SECRET` default is for local development only — generate a real one with `openssl rand -base64 32`.

## Production hardening checklist

- [ ] Replace the in-memory rate limiter with a Redis-backed Bucket4j store for multi-instance deployments
- [ ] Move `JWT_SECRET` and DB credentials to a secrets manager (Azure Key Vault / AWS Secrets Manager)
- [ ] Add Testcontainers-based integration tests against real PostgreSQL
- [ ] Wire Prometheus + Grafana dashboards off `/actuator/prometheus`
- [ ] Add an outbox table + async publisher if transactions must emit events (Kafka/RabbitMQ)

## Project structure

```
src/main/java/com/fincore/api/
├── config/          SecurityConfig, OpenApiConfig
├── controller/      REST endpoints (thin, annotated for Swagger)
├── dto/             request/ and response/ records with Bean Validation
├── entity/          JPA entities + enums, BaseEntity auditing
├── exception/       ApiException hierarchy + GlobalExceptionHandler
├── mapper/          MapStruct compile-time mappers
├── repository/      Spring Data JPA (incl. pessimistic-lock queries)
├── security/        JWT service/filter, rate limiting, correlation IDs
├── service/         Interfaces + impl/ (business logic, transactions)
└── util/            Account number + reference generators
src/main/resources/db/migration/   Flyway SQL migrations
```
