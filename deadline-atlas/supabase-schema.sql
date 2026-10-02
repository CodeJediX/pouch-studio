-- Deadline Atlas database schema for Supabase.
-- Apply in the Supabase SQL Editor for the target project.

create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

create table public.profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  username text not null unique
    check (username ~ '^[a-z0-9][a-z0-9._-]{2,29}$'),
  created_at timestamptz not null default now()
);

alter table public.profiles enable row level security;
alter table public.profiles force row level security;

revoke all on table public.profiles from anon, authenticated;
grant select, insert, update on table public.profiles to authenticated;
grant select on table public.profiles to service_role;

create policy "Users can read their profile"
on public.profiles for select
to authenticated
using ((select auth.uid()) = user_id);

create policy "Users can create their profile"
on public.profiles for insert
to authenticated
with check ((select auth.uid()) = user_id);

create policy "Users can update their profile"
on public.profiles for update
to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

create or replace function private.create_user_profile()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  normalized_username text := lower(btrim(new.raw_user_meta_data ->> 'username'));
begin
  if normalized_username is null
    or normalized_username !~ '^[a-z0-9][a-z0-9._-]{2,29}$' then
    raise exception 'A valid username is required';
  end if;

  insert into public.profiles (user_id, username)
  values (new.id, normalized_username);
  return new;
end;
$$;

revoke all on function private.create_user_profile()
  from public, anon, authenticated;

create trigger create_profile_after_signup
after insert on auth.users
for each row execute function private.create_user_profile();

create table public.competitions (
  user_id uuid not null references auth.users(id) on delete cascade,
  id text not null,
  name text not null check (char_length(name) between 1 and 160),
  deadline date not null,
  category text not null default 'Other',
  status text not null default 'active'
    check (status in ('active', 'review', 'done')),
  priority text not null default 'medium'
    check (priority in ('high', 'medium', 'low')),
  next_action text not null default '',
  notes text not null default '',
  milestones jsonb not null default '[]'::jsonb
    check (jsonb_typeof(milestones) = 'array'),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (user_id, id)
);

create index competitions_user_deadline_idx
  on public.competitions (user_id, deadline);

alter table public.competitions enable row level security;
alter table public.competitions force row level security;

revoke all on table public.competitions from anon, authenticated;
grant select, insert, update, delete on table public.competitions to authenticated;

create policy "Users can read their competitions"
on public.competitions for select
to authenticated
using ((select auth.uid()) = user_id);

create policy "Users can create their competitions"
on public.competitions for insert
to authenticated
with check ((select auth.uid()) = user_id);

create policy "Users can update their competitions"
on public.competitions for update
to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

create policy "Users can delete their competitions"
on public.competitions for delete
to authenticated
using ((select auth.uid()) = user_id);

-- A server-side first-run marker prevents sample or legacy data from being
-- restored after a user intentionally deletes every competition.
create table public.user_app_state (
  user_id uuid primary key references auth.users(id) on delete cascade,
  initialized_at timestamptz not null default now()
);

alter table public.user_app_state enable row level security;
alter table public.user_app_state force row level security;

revoke all on table public.user_app_state from anon, authenticated;
grant select, insert, update on table public.user_app_state to authenticated;

create policy "Users can read their app state"
on public.user_app_state for select
to authenticated
using ((select auth.uid()) = user_id);

create policy "Users can create their app state"
on public.user_app_state for insert
to authenticated
with check ((select auth.uid()) = user_id);

create policy "Users can update their app state"
on public.user_app_state for update
to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

-- Supabase projects created with automatic RLS use this privileged event-trigger
-- helper. It must not be callable through the Data API.
revoke execute on function public.rls_auto_enable()
  from public, anon, authenticated;
