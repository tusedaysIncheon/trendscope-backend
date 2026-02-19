-- Expand ticket_ledger.reason allowed values for hold/consume/release flow.
-- Old local schemas may still have a legacy check allowing only PURCHASE/USE/REFUND.

DO
$$
DECLARE
    v_conname TEXT;
BEGIN
    IF to_regclass('public.ticket_ledger') IS NULL THEN
        RETURN;
    END IF;

    -- Remove legacy reason-related CHECK constraints (e.g. ticket_ledger_reason_check).
    FOR v_conname IN
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE n.nspname = 'public'
          AND t.relname = 'ticket_ledger'
          AND c.contype = 'c'
          AND pg_get_constraintdef(c.oid) ILIKE '%reason%'
    LOOP
        EXECUTE format('ALTER TABLE public.ticket_ledger DROP CONSTRAINT %I', v_conname);
    END LOOP;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_ticket_ledger_reason') THEN
        ALTER TABLE public.ticket_ledger
            ADD CONSTRAINT chk_ticket_ledger_reason
                CHECK (reason IN ('PURCHASE', 'HOLD', 'CONSUME', 'RELEASE', 'USE', 'REFUND'));
    END IF;
END
$$;
