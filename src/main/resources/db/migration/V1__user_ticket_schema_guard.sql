-- Guard migration for existing local DBs created before user_ schema refactor.
-- This migration is idempotent and safe for both old and fresh databases.

DO
$$
BEGIN
    IF to_regclass('public.user_') IS NOT NULL THEN
        -- Legacy columns from old auth model.
        ALTER TABLE user_ DROP COLUMN IF EXISTS password;
        ALTER TABLE user_ DROP COLUMN IF EXISTS is_membership;
        ALTER TABLE user_ DROP COLUMN IF EXISTS is_social;

        -- New identity/ticket columns.
        ALTER TABLE user_ ADD COLUMN IF NOT EXISTS provider_user_id VARCHAR(255);
        ALTER TABLE user_ ADD COLUMN IF NOT EXISTS ticket_balance INTEGER NOT NULL DEFAULT 0;

        -- Backfill for old rows.
        UPDATE user_
           SET provider_user_id = CASE
                                      WHEN social_provider_type = 'EMAIL_OTP' THEN LOWER(COALESCE(email, username))
                                      ELSE COALESCE(provider_user_id, username)
               END
         WHERE provider_user_id IS NULL;

        UPDATE user_
           SET ticket_balance = 0
         WHERE ticket_balance IS NULL
            OR ticket_balance < 0;

        -- Required constraints.
        ALTER TABLE user_ ALTER COLUMN provider_user_id SET NOT NULL;
        ALTER TABLE user_ ALTER COLUMN social_provider_type SET NOT NULL;
        ALTER TABLE user_ ALTER COLUMN ticket_balance SET NOT NULL;
        ALTER TABLE user_ ALTER COLUMN ticket_balance SET DEFAULT 0;

        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_user_provider_type_user_id') THEN
            ALTER TABLE user_
                ADD CONSTRAINT uk_user_provider_type_user_id
                    UNIQUE (social_provider_type, provider_user_id);
        END IF;

        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_user_ticket_balance_non_negative') THEN
            ALTER TABLE user_
                ADD CONSTRAINT chk_user_ticket_balance_non_negative
                    CHECK (ticket_balance >= 0);
        END IF;
    END IF;
END
$$;

