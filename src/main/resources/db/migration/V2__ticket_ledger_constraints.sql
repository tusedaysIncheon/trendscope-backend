-- Ensure ticket_ledger constraints/indexes for idempotent accounting logic.

DO
$$
BEGIN
    IF to_regclass('public.ticket_ledger') IS NOT NULL THEN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_ticket_ledger_user_reason_ref_id') THEN
            ALTER TABLE ticket_ledger
                ADD CONSTRAINT uk_ticket_ledger_user_reason_ref_id
                    UNIQUE (user_id, reason, ref_id);
        END IF;

        IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_ticket_ledger_user_created') THEN
            CREATE INDEX idx_ticket_ledger_user_created
                ON ticket_ledger (user_id, created_date);
        END IF;
    END IF;
END
$$;

