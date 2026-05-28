insert into inference_run (case_id, execution_mode, model_version, status, started_at, finished_at, metrics_json, failure_details_json)
select c.id,
       null,
       coalesce('seeded-demo:' || c.demo_manifest_version, 'seeded-demo'),
       'COMPLETED',
       coalesce(c.updated_at, c.created_at, current_timestamp),
       coalesce(c.updated_at, c.created_at, current_timestamp),
       null,
       null
from analysis_case c
where c.origin = 'SEEDED_DEMO'
  and c.status = 'COMPLETED'
  and not exists (
      select 1
      from inference_run run
      where run.case_id = c.id
  );
