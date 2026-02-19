-- Add measurement_model for analyze job pipeline (quick/premium).

DO
$$
BEGIN
    IF to_regclass('public.analyze_job') IS NULL THEN
        RETURN;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'analyze_job'
          AND column_name = 'measurement_model'
    ) THEN
        ALTER TABLE analyze_job
            ADD COLUMN measurement_model VARCHAR(20);
    END IF;

    UPDATE analyze_job
    SET measurement_model = CASE
                                WHEN mode = 'QUICK_1VIEW' THEN 'quick'
                                ELSE 'premium'
        END
    WHERE measurement_model IS NULL
       OR measurement_model = '';

    UPDATE analyze_job
    SET measurement_model = 'premium'
    WHERE measurement_model NOT IN ('quick', 'premium')
       OR measurement_model IS NULL;

    ALTER TABLE analyze_job
        ALTER COLUMN measurement_model SET DEFAULT 'premium';

    ALTER TABLE analyze_job
        ALTER COLUMN measurement_model SET NOT NULL;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_analyze_job_measurement_model') THEN
        ALTER TABLE analyze_job
            ADD CONSTRAINT chk_analyze_job_measurement_model
                CHECK (measurement_model IN ('quick', 'premium'));
    END IF;
END
$$;
