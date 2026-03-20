delete from inference_run run
where exists (
    select 1
    from analysis_case c
    where c.id = run.case_id
      and c.origin = 'SEEDED_DEMO'
)
  and run.execution_mode is null
  and run.model_version like 'seeded-demo%';
