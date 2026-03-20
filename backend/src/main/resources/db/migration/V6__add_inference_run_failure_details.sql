alter table inference_run
    add column if not exists failure_details_json text;
