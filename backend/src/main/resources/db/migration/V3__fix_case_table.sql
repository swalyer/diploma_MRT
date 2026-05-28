-- Repair migration for legacy case table naming and column shape.
-- V1 already creates public.analysis_case on greenfield environments,
-- so V3 only reconciles upgraded databases.

DO $$
BEGIN
    IF to_regclass('public.analysis_case') IS NULL THEN
        IF to_regclass('public.case_entity') IS NOT NULL THEN
            ALTER TABLE public.case_entity RENAME TO analysis_case;
        ELSIF to_regclass('public."case"') IS NOT NULL THEN
            ALTER TABLE public."case" RENAME TO analysis_case;
        END IF;
    END IF;
END $$;

ALTER TABLE IF EXISTS public.analysis_case
    ADD COLUMN IF NOT EXISTS patient_pseudo_id varchar(255),
    ADD COLUMN IF NOT EXISTS modality varchar(16),
    ADD COLUMN IF NOT EXISTS status varchar(32),
    ADD COLUMN IF NOT EXISTS created_by bigint,
    ADD COLUMN IF NOT EXISTS created_at timestamp,
    ADD COLUMN IF NOT EXISTS updated_at timestamp;

DO $$
BEGIN
    IF to_regclass('public.analysis_case') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM pg_constraint
           WHERE conname = 'analysis_case_created_by_fkey'
       ) THEN
        ALTER TABLE public.analysis_case
            ADD CONSTRAINT analysis_case_created_by_fkey
            FOREIGN KEY (created_by) REFERENCES public.app_user(id);
    END IF;
END $$;
