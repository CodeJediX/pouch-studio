create table if not exists public.account_setup_tokens (
  token_hash text primary key check (token_hash ~ '^[0-9a-f]{64}$'),
  user_id uuid not null references auth.users(id) on delete cascade,
  expires_at timestamptz not null,
  used_at timestamptz,
  created_at timestamptz not null default now()
);

alter table public.account_setup_tokens enable row level security;
alter table public.account_setup_tokens force row level security;

revoke all on table public.account_setup_tokens from public, anon, authenticated;
grant select, update, insert, delete on table public.account_setup_tokens to service_role;

create or replace function public.claim_account_setup_token(p_token_hash text)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  claimed_user_id uuid;
begin
  update public.account_setup_tokens
     set used_at = now()
   where token_hash = p_token_hash
     and used_at is null
     and expires_at > now()
  returning user_id into claimed_user_id;

  return claimed_user_id;
end;
$$;

revoke all on function public.claim_account_setup_token(text) from public, anon, authenticated;
grant execute on function public.claim_account_setup_token(text) to service_role;
