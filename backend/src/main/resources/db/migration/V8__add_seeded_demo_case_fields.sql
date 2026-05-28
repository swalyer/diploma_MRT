alter table analysis_case
    add column if not exists origin varchar(32) not null default 'LIVE_PROCESSED',
    add column if not exists demo_case_slug varchar(128),
    add column if not exists demo_manifest_version varchar(32),
    add column if not exists source_dataset varchar(255),
    add column if not exists source_attribution text,
    add column if not exists demo_category varchar(32);

create unique index if not exists ux_analysis_case_demo_identity
    on analysis_case (demo_case_slug, demo_manifest_version)
    where demo_case_slug is not null and demo_manifest_version is not null;
