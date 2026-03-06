insert into app_user(email,password_hash,role,created_at)
values
  ('admin@demo.local','$2a$10$q57aQILQckcr4j60V.qcBebh4hNxXKM2ohyBiPE6nxgTsSTx0JzRy','ADMIN',now()),
  ('doctor@demo.local','$2a$10$q57aQILQckcr4j60V.qcBebh4hNxXKM2ohyBiPE6nxgTsSTx0JzRy','DOCTOR',now())
on conflict (email) do nothing;
