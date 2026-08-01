-- ============================================================
-- RehabCardia — Testing Portal schema (Supabase)
-- Run this once in the Supabase SQL Editor.
-- Project: https://eilwwgahluubrrsjgroo.supabase.co
-- ============================================================
--
-- NOTE ON SECURITY:
-- This portal uses email-only access (no passwords), so the
-- browser talks to Supabase with the anon/publishable key.
-- That means anyone who reads the page source can call these
-- tables directly. The policies below are deliberately open so
-- the no-password flow works. Do NOT put patient data, PHI, or
-- anything confidential in these tables — bug reports only.
-- ============================================================


-- ---------- 1. Allowed testers ----------
-- status: 'approved' = can sign in, 'pending' = requested access and waiting,
--         'declined' = admin turned them down.
create table if not exists public.test_testers (
  email       text primary key,
  name        text,
  status      text not null default 'approved' check (status in ('approved', 'pending', 'declined')),
  note        text,
  created_at  timestamptz not null default now()
);

-- If the table already existed from an earlier run, add the new columns:
alter table public.test_testers add column if not exists status text not null default 'approved';
alter table public.test_testers add column if not exists note text;

create index if not exists test_testers_status_idx on public.test_testers (status);

-- ---------- 2. Reports ----------
create table if not exists public.test_reports (
  id           uuid primary key default gen_random_uuid(),
  email        text not null,
  category     text not null check (category in ('bug', 'feature', 'improvement')),
  role         text not null default 'patient' check (role in ('patient', 'doctor')),
  page         text,
  title        text not null,
  description  text not null,
  severity     text default 'medium' check (severity in ('low', 'medium', 'high', 'critical')),
  device       text,
  status       text not null default 'open' check (status in ('open', 'in_progress', 'resolved', 'wont_fix')),
  image_urls   text[] default '{}',
  admin_note   text,
  created_at   timestamptz not null default now()
);

-- If the table already existed from an earlier run, add the new columns:
alter table public.test_reports add column if not exists role text not null default 'patient';
alter table public.test_reports add column if not exists page text;

create index if not exists test_reports_category_idx on public.test_reports (category);
create index if not exists test_reports_role_idx     on public.test_reports (role);
create index if not exists test_reports_email_idx    on public.test_reports (email);
create index if not exists test_reports_created_idx  on public.test_reports (created_at desc);


-- ---------- 3. Row Level Security ----------
alter table public.test_testers enable row level security;
alter table public.test_reports enable row level security;

-- Testers list: readable + writable by anon (the page gates this in the UI).
drop policy if exists "testers_read"   on public.test_testers;
drop policy if exists "testers_write"  on public.test_testers;
drop policy if exists "testers_update" on public.test_testers;
drop policy if exists "testers_delete" on public.test_testers;

create policy "testers_read"   on public.test_testers for select using (true);
create policy "testers_write"  on public.test_testers for insert with check (true);
create policy "testers_update" on public.test_testers for update using (true) with check (true);
create policy "testers_delete" on public.test_testers for delete using (true);

-- Reports: read/insert/update open to anon.
drop policy if exists "reports_read"   on public.test_reports;
drop policy if exists "reports_insert" on public.test_reports;
drop policy if exists "reports_update" on public.test_reports;
drop policy if exists "reports_delete" on public.test_reports;

create policy "reports_read"   on public.test_reports for select using (true);
create policy "reports_insert" on public.test_reports for insert with check (true);
create policy "reports_update" on public.test_reports for update using (true) with check (true);
create policy "reports_delete" on public.test_reports for delete using (true);


-- ---------- 4. Storage bucket for screenshots ----------
insert into storage.buckets (id, name, public)
values ('test-screenshots', 'test-screenshots', true)
on conflict (id) do update set public = true;

drop policy if exists "screenshots_read"   on storage.objects;
drop policy if exists "screenshots_upload" on storage.objects;
drop policy if exists "screenshots_delete" on storage.objects;

create policy "screenshots_read" on storage.objects
  for select using (bucket_id = 'test-screenshots');

create policy "screenshots_upload" on storage.objects
  for insert with check (bucket_id = 'test-screenshots');

create policy "screenshots_delete" on storage.objects
  for delete using (bucket_id = 'test-screenshots');


-- ---------- 5. Seed the first testers (optional) ----------
insert into public.test_testers (email, name, status) values
  ('shanebraiden48@gmail.com', 'Shane',             'approved'),
  ('sr.cardiocare@gmail.com',  'RehabCardia Admin', 'approved')
on conflict (email) do nothing;
