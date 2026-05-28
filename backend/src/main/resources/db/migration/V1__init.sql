create table app_user (
  id bigserial primary key,
  email varchar(255) unique not null,
  password_hash varchar(255) not null,
  role varchar(32) not null,
  created_at timestamp not null
);
create table analysis_case (
  id bigserial primary key,
  patient_pseudo_id varchar(255) not null,
  modality varchar(16) not null,
  status varchar(32) not null,
  created_by bigint not null references app_user(id),
  created_at timestamp not null,
  updated_at timestamp not null
);
create table artifact (
  id bigserial primary key,
  case_id bigint not null references analysis_case(id),
  type varchar(64) not null,
  file_path text not null,
  mime_type varchar(128) not null,
  created_at timestamp not null
);
create table inference_run (
  id bigserial primary key,
  case_id bigint not null references analysis_case(id),
  model_version varchar(64) not null,
  status varchar(32) not null,
  started_at timestamp not null,
  finished_at timestamp,
  metrics_json text
);
create table finding (
  id bigserial primary key,
  case_id bigint not null references analysis_case(id),
  type varchar(64) not null,
  label varchar(255) not null,
  confidence double precision not null,
  size_mm double precision,
  volume_mm3 double precision,
  location_json text
);
create table report (
  id bigserial primary key,
  case_id bigint unique not null references analysis_case(id),
  report_text text not null,
  report_json text not null,
  created_at timestamp not null
);
create table audit_event (
  id bigserial primary key,
  user_id bigint,
  case_id bigint,
  action varchar(128) not null,
  details_json text,
  created_at timestamp not null
);
