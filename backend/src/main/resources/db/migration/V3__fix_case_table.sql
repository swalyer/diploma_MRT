-- Reconcile legacy schema naming with current JPA mapping (@Table(name = 'analysis_case')).
-- This migration is safe for both fresh databases and upgraded environments.

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

CREATE TABLE IF NOT EXISTS public.analysis_case (
    id bigserial PRIMARY KEY,
    patient_pseudo_id varchar(255) NOT NULL,
    modality varchar(16) NOT NULL,
    status varchar(32) NOT NULL,
    created_by bigint NOT NULL REFERENCES public.app_user(id),
    created_at timestamp NOT NULL,
    updated_at timestamp NOT NULL
);
