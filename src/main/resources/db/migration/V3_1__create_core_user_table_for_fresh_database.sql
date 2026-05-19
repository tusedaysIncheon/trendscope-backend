-- Create the core user table for fresh databases.
-- Older environments already had this table from Hibernate ddl-auto, while
-- fresh RDS instances need it before later Flyway migrations add foreign keys.

DO
$$
BEGIN
    IF to_regclass('public.user_') IS NULL THEN
        CREATE TABLE user_
        (
            id                     BIGSERIAL PRIMARY KEY,
            username               VARCHAR(255) NOT NULL UNIQUE,
            is_lock                BOOLEAN      NOT NULL DEFAULT FALSE,
            social_provider_type   VARCHAR(50)  NOT NULL,
            provider_user_id       VARCHAR(255) NOT NULL,
            role_type              VARCHAR(50)  NOT NULL,
            quick_ticket_balance   INTEGER      NOT NULL DEFAULT 1,
            premium_ticket_balance INTEGER      NOT NULL DEFAULT 0,
            email                  VARCHAR(255) NOT NULL,
            created_date           TIMESTAMP,
            updated_date           TIMESTAMP
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_user_provider_type_user_id') THEN
        ALTER TABLE user_
            ADD CONSTRAINT uk_user_provider_type_user_id
                UNIQUE (social_provider_type, provider_user_id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_user_ticket_balances_non_negative') THEN
        ALTER TABLE user_
            ADD CONSTRAINT chk_user_ticket_balances_non_negative
                CHECK (quick_ticket_balance >= 0 AND premium_ticket_balance >= 0);
    END IF;
END
$$;
