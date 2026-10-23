-- Balances are stored on the account row so a transfer can lock it, and every change is
-- also recorded as a signed ledger entry, so balance = sum(entries) for every account.

CREATE TABLE accounts (
    id            UUID         PRIMARY KEY,
    owner_name    VARCHAR(100) NOT NULL,
    currency      VARCHAR(3)   NOT NULL,
    balance_minor BIGINT       NOT NULL CHECK (balance_minor >= 0),
    created_at    TIMESTAMPTZ  NOT NULL
);

CREATE TABLE transfers (
    id              UUID        PRIMARY KEY,
    from_account_id UUID        NOT NULL REFERENCES accounts (id),
    to_account_id   UUID        NOT NULL REFERENCES accounts (id),
    amount_minor    BIGINT      NOT NULL CHECK (amount_minor > 0),
    currency        VARCHAR(3)  NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    CHECK (from_account_id <> to_account_id)
);

CREATE TABLE ledger_entries (
    id           UUID        PRIMARY KEY,
    account_id   UUID        NOT NULL REFERENCES accounts (id),
    transfer_id  UUID        REFERENCES transfers (id),
    entry_type   VARCHAR(16) NOT NULL,
    amount_minor BIGINT      NOT NULL CHECK (amount_minor <> 0),
    created_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX ledger_entries_account_idx ON ledger_entries (account_id, created_at DESC);

CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(64) PRIMARY KEY,
    request_hash    VARCHAR(64) NOT NULL,
    transfer_id     UUID        REFERENCES transfers (id),
    created_at      TIMESTAMPTZ NOT NULL
);
