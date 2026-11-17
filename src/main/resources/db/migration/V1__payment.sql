-- One row for every payment we start. The money is a whole number of paise (49900 = 499.00 rupees).
CREATE TABLE payment (
    id                BINARY(16)   NOT NULL,
    user_id           VARCHAR(64)  NOT NULL,
    idempotency_key   VARCHAR(100) NOT NULL,
    order_ref         VARCHAR(64)  NOT NULL,
    amount            BIGINT       NOT NULL,
    currency          VARCHAR(3)   NOT NULL,
    description       VARCHAR(255),
    status            VARCHAR(20)  NOT NULL,
    gateway_reference VARCHAR(100),
    payment_url       VARCHAR(500),
    failure_reason    VARCHAR(500),
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    -- the same person cannot use the same key twice
    UNIQUE KEY uk_payment_user_key (user_id, idempotency_key),
    KEY idx_payment_order (order_ref),
    KEY idx_payment_link (gateway_reference)
);
