CREATE TABLE accounts (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    account_number  VARCHAR(10) NOT NULL UNIQUE,
    user_id         UUID NOT NULL REFERENCES users (id),
    account_type    VARCHAR(20) NOT NULL,
    currency        VARCHAR(3)  NOT NULL DEFAULT 'NGN',
    balance         NUMERIC(19, 4) NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0)
);

CREATE INDEX idx_accounts_user ON accounts (user_id);
CREATE INDEX idx_accounts_number ON accounts (account_number);

CREATE TABLE transactions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    reference       VARCHAR(40) NOT NULL UNIQUE,
    source_account_id       UUID REFERENCES accounts (id),
    destination_account_id  UUID REFERENCES accounts (id),
    amount          NUMERIC(19, 4) NOT NULL,
    currency        VARCHAR(3) NOT NULL DEFAULT 'NGN',
    type            VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    narration       VARCHAR(255),
    idempotency_key VARCHAR(64) UNIQUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    CONSTRAINT chk_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_txn_source ON transactions (source_account_id);
CREATE INDEX idx_txn_destination ON transactions (destination_account_id);
CREATE INDEX idx_txn_reference ON transactions (reference);
CREATE INDEX idx_txn_created ON transactions (created_at DESC);
