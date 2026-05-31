-- Real-pipeline model versions are descriptive provenance strings, e.g.
-- "real-ts:heuristic-lesion:nnU-Net v2 [Dataset501_AtlasMRILesion]@cuda",
-- which exceed the original varchar(64). Widen to preserve honest provenance
-- instead of truncating it.
alter table inference_run alter column model_version type varchar(255);
