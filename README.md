# Payments Ledger API

A double-entry payments ledger REST API in Java 21 and Spring Boot 3, backed by PostgreSQL.
Transfers are **idempotent** (a retried request never moves money twice), **atomic**, and
**safe under concurrency** (opposing transfers cannot deadlock or overdraw an account).

## API

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/v1/accounts` | Open an account: `{ownerName, currency, openingBalanceMinor}` |
| `GET` | `/api/v1/accounts/{id}` | Current balance |
| `GET` | `/api/v1/accounts/{id}/entries` | Latest 100 ledger entries |
| `POST` | `/api/v1/transfers` | Requires an `Idempotency-Key` header (8–64 chars of `[A-Za-z0-9_-]`) |
| `GET` | `/api/v1/transfers/{id}` | A single transfer |

Amounts are integer **minor units** (cents) so arithmetic is exact. Errors are returned as
RFC 9457 problem details (`application/problem+json`).

| Situation | Status |
|---|---|
| New transfer | `201 Created` |
| Retry with the same key and body | `200 OK` + `Idempotent-Replayed: true`, original transfer returned |
| Same key, different body | `422` |
| Insufficient funds | `409` |
| Currency mismatch | `422` |
| Validation error, missing key, same source and destination | `400` |
| Unknown account or transfer | `404` |

## Design decisions

**Double-entry ledger.** Every transfer writes one negative `DEBIT` and one positive `CREDIT`
entry. The balance is also stored on the account row (so it can be locked), and the invariant
`balance = sum(entries)` is checked by the tests. A database `CHECK (balance_minor >= 0)` is a
second line of defence against overdrafts.

**Idempotency.** The service claims the key with
`INSERT ... ON CONFLICT (idempotency_key) DO NOTHING` inside the transfer's transaction:

- If the insert succeeds, this request owns the key and performs the transfer.
- If another request is using the same key concurrently, PostgreSQL makes the insert wait on the
  primary-key index until that transaction finishes. If it committed, the request replays its
  result; if it rolled back, the insert succeeds and the request proceeds.
- The key stores a SHA-256 hash of the request, so reusing a key with a different body is rejected.
- A failed transfer (for example insufficient funds) rolls back the key too, so the client can
  retry with the same key once the problem is fixed.

**Concurrency.** Both accounts are locked with `SELECT ... FOR UPDATE`, always in ascending id
order. Two transfers A→B and B→A therefore request the locks in the same order and cannot deadlock.

**Security and hygiene.** Bean Validation on every input, no stack traces or internal messages in
error responses, database credentials only from environment variables, the container runs as a
non-root user, and CI scans for secrets (Gitleaks), vulnerable patterns (CodeQL), and vulnerable
dependencies (dependency review and Dependabot).

## Testing

```bash
./mvnw verify    # needs Docker running for Testcontainers
```

- **Unit tests:** request hashing and account debit/credit rules, including overflow.
- **Integration tests** (Testcontainers, real PostgreSQL 16): happy path, idempotent replay,
  key reuse with a different body, insufficient funds, currency mismatch, validation, 404s.
- **Concurrency tests:** 200 opposing transfers fired from 16 threads at once (money is conserved,
  no balance goes negative, no deadlocks), and 20 simultaneous retries with one key (exactly one
  transfer is created).
- Coverage report: `target/site/jacoco/index.html`.

## Run locally

```bash
DB_PASSWORD=choose-one docker compose up --build
curl -X POST localhost:8080/api/v1/accounts -H 'Content-Type: application/json' \
  -d '{"ownerName":"Ada","currency":"USD","openingBalanceMinor":1000}'
```

Set `API_PORT` if port 8080 is already in use.

## Not in scope

Authentication and authorisation, multi-currency conversion, and expiry of old idempotency keys.
In a production system these would come next.
