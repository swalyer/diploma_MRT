UPDATE app_user
SET email = lower(trim(email))
WHERE email <> lower(trim(email));

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM (
            SELECT lower(email) AS normalized_email, COUNT(*) AS duplicates
            FROM app_user
            GROUP BY lower(email)
            HAVING COUNT(*) > 1
        ) duplicate_emails
    ) THEN
        RAISE EXCEPTION 'Duplicate user emails differing only by case exist in app_user; resolve them before applying V5';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_app_user_email_lower ON app_user ((lower(email)));
