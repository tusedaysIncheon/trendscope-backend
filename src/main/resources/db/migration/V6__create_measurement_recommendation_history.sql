-- Persist measurement + LLM recommendation snapshot for mypage history.

DO
$$
BEGIN
    IF to_regclass('public.measurement_recommendation_history') IS NULL THEN
        CREATE TABLE measurement_recommendation_history
        (
            id                BIGSERIAL PRIMARY KEY,
            user_id           BIGINT       NOT NULL REFERENCES user_ (id) ON DELETE CASCADE,
            user_seq          BIGINT       NOT NULL,
            analyze_job_id    BIGINT       NOT NULL REFERENCES analyze_job (id) ON DELETE CASCADE,
            mode              VARCHAR(20)  NOT NULL,
            measurement_model VARCHAR(20)  NOT NULL,
            front_image_key   VARCHAR(512) NOT NULL,
            side_image_key    VARCHAR(512),
            glb_object_key    VARCHAR(512) NOT NULL,
            result_json       TEXT         NOT NULL,
            llm_response_json TEXT         NOT NULL,
            llm_model         VARCHAR(100),
            prompt_version    VARCHAR(50),
            created_date      TIMESTAMP,
            updated_date      TIMESTAMP
        );
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_measurement_recommendation_history_user_seq'
    ) THEN
        ALTER TABLE measurement_recommendation_history
            ADD CONSTRAINT uk_measurement_recommendation_history_user_seq
                UNIQUE (user_id, user_seq);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_measurement_recommendation_history_analyze_job'
    ) THEN
        ALTER TABLE measurement_recommendation_history
            ADD CONSTRAINT uk_measurement_recommendation_history_analyze_job
                UNIQUE (analyze_job_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_measurement_recommendation_history_mode'
    ) THEN
        ALTER TABLE measurement_recommendation_history
            ADD CONSTRAINT chk_measurement_recommendation_history_mode
                CHECK (mode IN ('STANDARD_2VIEW', 'QUICK_1VIEW'));
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_measurement_recommendation_history_measurement_model'
    ) THEN
        ALTER TABLE measurement_recommendation_history
            ADD CONSTRAINT chk_measurement_recommendation_history_measurement_model
                CHECK (measurement_model IN ('quick', 'premium'));
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE indexname = 'idx_measurement_recommendation_history_user_created'
    ) THEN
        CREATE INDEX idx_measurement_recommendation_history_user_created
            ON measurement_recommendation_history (user_id, created_date);
    END IF;
END
$$;
