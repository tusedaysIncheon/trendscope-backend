-- New users receive one free quick ticket by default.
-- Existing users are not changed by this migration.

DO
$$
BEGIN
    IF to_regclass('public.user_') IS NULL THEN
        RETURN;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'user_'
          AND column_name = 'quick_ticket_balance'
    ) THEN
        ALTER TABLE user_
            ALTER COLUMN quick_ticket_balance SET DEFAULT 1;
    END IF;
END
$$;
