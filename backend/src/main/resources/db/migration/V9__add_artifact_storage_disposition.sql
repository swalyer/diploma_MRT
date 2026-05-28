alter table artifact
    add column if not exists storage_disposition varchar(32) not null default 'MANAGED';
