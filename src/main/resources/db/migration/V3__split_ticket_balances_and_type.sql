-- Split single ticket_balance into quick/premium balances.
-- Legacy balance is migrated to premium by default.

DO
$$
BEGIN
    IF to_regclass('public.user_') IS NOT NULL THEN
        ALTER TABLE user_ ADD COLUMN IF NOT EXISTS quick_ticket_balance INTEGER NOT NULL DEFAULT 0;
        ALTER TABLE user_ ADD COLUMN IF NOT EXISTS premium_ticket_balance INTEGER NOT NULL DEFAULT 0;

        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'user_'
              AND column_name = 'ticket_balance'
        ) THEN
            UPDATE user_
               SET premium_ticket_balance = COALESCE(ticket_balance, 0)
             WHERE premium_ticket_balance IS NULL
                OR premium_ticket_balance = 0;

            ALTER TABLE user_ DROP COLUMN IF EXISTS ticket_balance;
        END IF;

        UPDATE user_ SET quick_ticket_balance = 0 WHERE quick_ticket_balance IS NULL OR quick_ticket_balance < 0;
        UPDATE user_ SET premium_ticket_balance = 0 WHERE premium_ticket_balance IS NULL OR premium_ticket_balance < 0;

        ALTER TABLE user_ ALTER COLUMN quick_ticket_balance SET NOT NULL;
        ALTER TABLE user_ ALTER COLUMN quick_ticket_balance SET DEFAULT 0;
        ALTER TABLE user_ ALTER COLUMN premium_ticket_balance SET NOT NULL;
        ALTER TABLE user_ ALTER COLUMN premium_ticket_balance SET DEFAULT 0;

        IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_user_ticket_balance_non_negative') THEN
            ALTER TABLE user_ DROP CONSTRAINT chk_user_ticket_balance_non_negative;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_user_ticket_balances_non_negative') THEN
            ALTER TABLE user_
                ADD CONSTRAINT chk_user_ticket_balances_non_negative
                    CHECK (quick_ticket_balance >= 0 AND premium_ticket_balance >= 0);
        END IF;
    END IF;

    IF to_regclass('public.ticket_ledger') IS NOT NULL THEN
        ALTER TABLE ticket_ledger ADD COLUMN IF NOT EXISTS ticket_type VARCHAR(20);
        UPDATE ticket_ledger SET ticket_type = 'PREMIUM' WHERE ticket_type IS NULL;
        ALTER TABLE ticket_ledger ALTER COLUMN ticket_type SET NOT NULL;

        IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_ticket_ledger_user_reason_ref_id') THEN
            ALTER TABLE ticket_ledger DROP CONSTRAINT uk_ticket_ledger_user_reason_ref_id;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_ticket_ledger_user_type_reason_ref_id') THEN
            ALTER TABLE ticket_ledger
                ADD CONSTRAINT uk_ticket_ledger_user_type_reason_ref_id
                    UNIQUE (user_id, ticket_type, reason, ref_id);
        END IF;

        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_ticket_ledger_ticket_type') THEN
            ALTER TABLE ticket_ledger
                ADD CONSTRAINT chk_ticket_ledger_ticket_type
                    CHECK (ticket_type IN ('QUICK', 'PREMIUM'));
        END IF;

        IF EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_ticket_ledger_user_created') THEN
            DROP INDEX idx_ticket_ledger_user_created;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_ticket_ledger_user_type_created') THEN
            CREATE INDEX idx_ticket_ledger_user_type_created
                ON ticket_ledger (user_id, ticket_type, created_date);
        END IF;
    END IF;
END
$$;

