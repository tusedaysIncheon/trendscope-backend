-- Analyze pipeline job table for upload->modal->status flow.

DO
$$
BEGIN
    IF to_regclass('public.analyze_job') IS NULL THEN
        CREATE TABLE analyze_job
        (
            id                  BIGSERIAL PRIMARY KEY,
            job_id              VARCHAR(64)  NOT NULL UNIQUE,
            user_id             BIGINT       NOT NULL REFERENCES user_ (id) ON DELETE CASCADE,
            mode                VARCHAR(20)  NOT NULL,
            status              VARCHAR(20)  NOT NULL,
            front_image_key     VARCHAR(512) NOT NULL,
            side_image_key      VARCHAR(512),
            glb_object_key      VARCHAR(512) NOT NULL,
            height_cm           DOUBLE PRECISION,
            weight_kg           DOUBLE PRECISION,
            gender              VARCHAR(20),
            quality_mode        VARCHAR(20),
            normalize_with_anny BOOLEAN      NOT NULL DEFAULT TRUE,
            output_pose         VARCHAR(20),
            result_json         TEXT,
            error_code          VARCHAR(100),
            error_detail        TEXT,
            queued_at           TIMESTAMP,
            started_at          TIMESTAMP,
            completed_at        TIMESTAMP,
            created_date        TIMESTAMP,
            updated_date        TIMESTAMP
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_analyze_job_mode') THEN
        ALTER TABLE analyze_job
            ADD CONSTRAINT chk_analyze_job_mode
                CHECK (mode IN ('STANDARD_2VIEW', 'QUICK_1VIEW'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_analyze_job_status') THEN
        ALTER TABLE analyze_job
            ADD CONSTRAINT chk_analyze_job_status
                CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_analyze_job_quality_mode') THEN
        ALTER TABLE analyze_job
            ADD CONSTRAINT chk_analyze_job_quality_mode
                CHECK (quality_mode IS NULL OR quality_mode IN ('fast', 'accurate'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_analyze_job_output_pose') THEN
        ALTER TABLE analyze_job
            ADD CONSTRAINT chk_analyze_job_output_pose
                CHECK (output_pose IS NULL OR output_pose IN ('A_POSE', 'PHOTO_POSE'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_analyze_job_user_created') THEN
        CREATE INDEX idx_analyze_job_user_created
            ON analyze_job (user_id, created_date);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_analyze_job_status_created') THEN
        CREATE INDEX idx_analyze_job_status_created
            ON analyze_job (status, created_date);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_analyze_job_completed') THEN
        CREATE INDEX idx_analyze_job_completed
            ON analyze_job (completed_at);
    END IF;
END
$$;
