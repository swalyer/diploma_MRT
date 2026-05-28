DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'artifact'
          AND column_name = 'file_path'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'artifact'
          AND column_name = 'object_key'
    ) THEN
        ALTER TABLE artifact RENAME COLUMN file_path TO object_key;
    END IF;
END $$;

ALTER TABLE artifact
    ADD COLUMN IF NOT EXISTS original_file_name varchar(255);

UPDATE artifact
SET original_file_name = regexp_replace(object_key, '^.*/', '')
WHERE original_file_name IS NULL
  AND object_key IS NOT NULL;

ALTER TABLE artifact
    ALTER COLUMN original_file_name SET NOT NULL;
