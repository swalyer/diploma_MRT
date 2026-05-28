ALTER TABLE inference_run
    ADD COLUMN IF NOT EXISTS execution_mode varchar(32);
