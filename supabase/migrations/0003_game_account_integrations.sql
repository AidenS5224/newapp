alter table public.games add column if not exists slug text;
alter table public.games add column if not exists publisher text;
alter table public.games add column if not exists icon_url text;
alter table public.games add column if not exists supported_platforms jsonb not null default '[]'::jsonb;
alter table public.games add column if not exists is_active boolean not null default true;

update public.games
set slug = id
where slug is null;

create table if not exists public.game_providers (
  id uuid primary key default gen_random_uuid(),
  provider_key text not null unique,
  display_name text not null,
  enabled boolean not null default false,
  configuration_status text not null default 'not_configured',
  health_status text not null default 'unknown',
  last_health_check_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.provider_game_support (
  id uuid primary key default gen_random_uuid(),
  provider_id uuid not null references public.game_providers(id) on delete cascade,
  game_id text not null references public.games(id) on delete cascade,
  capabilities jsonb not null default '[]'::jsonb,
  enabled boolean not null default false,
  requires_approval boolean not null default false,
  supported_platforms jsonb not null default '[]'::jsonb,
  supported_regions jsonb not null default '[]'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (provider_id, game_id)
);

create table if not exists public.user_game_accounts (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  provider_id uuid not null references public.game_providers(id) on delete restrict,
  game_id text not null references public.games(id) on delete restrict,
  platform text not null default '',
  region text not null default '',
  provider_account_id text not null default '',
  provider_account_name text not null default '',
  display_name text not null default '',
  verification_status text not null default 'public_account_unverified'
    check (verification_status in (
      'verified_oauth',
      'verified_challenge',
      'public_account_unverified',
      'user_provided_unverified',
      'verification_unavailable',
      'verification_failed'
    )),
  verification_method text not null default '',
  visibility text not null default 'private' check (visibility in ('public', 'connections', 'private')),
  use_for_matchmaking boolean not null default false,
  public_rank boolean not null default false,
  public_stats boolean not null default false,
  public_recent_matches boolean not null default false,
  auto_sync_enabled boolean not null default true,
  last_successful_sync_at timestamptz,
  last_sync_attempt_at timestamptz,
  sync_status text not null default 'never_synced',
  sync_error_code text not null default '',
  provider_metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.linked_authorizations (
  id uuid primary key default gen_random_uuid(),
  user_game_account_id uuid references public.user_game_accounts(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  provider_id uuid not null references public.game_providers(id) on delete cascade,
  encrypted_access_token text not null default '',
  encrypted_refresh_token text not null default '',
  token_type text not null default '',
  scopes jsonb not null default '[]'::jsonb,
  expires_at timestamptz,
  provider_metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.game_profile_snapshots (
  id uuid primary key default gen_random_uuid(),
  user_game_account_id uuid not null references public.user_game_accounts(id) on delete cascade,
  provider text not null,
  normalized_profile jsonb not null default '{}'::jsonb,
  normalized_stats jsonb not null default '{}'::jsonb,
  normalized_rank jsonb not null default '{}'::jsonb,
  data_freshness text not null default 'fresh',
  fetched_at timestamptz not null default now(),
  expires_at timestamptz,
  source_type text not null default 'approved_third_party'
    check (source_type in ('official_api', 'approved_third_party', 'user_provided')),
  safe_provider_metadata jsonb not null default '{}'::jsonb
);

create table if not exists public.manual_game_profiles (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  game_id text not null references public.games(id) on delete restrict,
  platform text not null default '',
  username text not null default '',
  region text not null default '',
  rank_text text not null default '',
  preferred_roles jsonb not null default '[]'::jsonb,
  preferred_modes jsonb not null default '[]'::jsonb,
  play_style jsonb not null default '[]'::jsonb,
  experience_level text not null default '',
  notes text not null default '',
  verification_status text not null default 'user_provided_unverified'
    check (verification_status = 'user_provided_unverified'),
  visibility text not null default 'private' check (visibility in ('public', 'connections', 'private')),
  use_for_matchmaking boolean not null default false,
  updated_at timestamptz not null default now(),
  created_at timestamptz not null default now()
);

create table if not exists public.sync_jobs (
  id uuid primary key default gen_random_uuid(),
  account_id uuid references public.user_game_accounts(id) on delete cascade,
  provider_id uuid references public.game_providers(id) on delete cascade,
  job_type text not null default 'profile_sync',
  status text not null default 'scheduled',
  attempts integer not null default 0,
  scheduled_at timestamptz not null default now(),
  started_at timestamptz,
  completed_at timestamptz,
  error_code text not null default '',
  idempotency_key text not null default gen_random_uuid()::text,
  created_at timestamptz not null default now(),
  unique (idempotency_key)
);

create table if not exists public.provider_request_events (
  id uuid primary key default gen_random_uuid(),
  provider_id uuid references public.game_providers(id) on delete set null,
  game_id text references public.games(id) on delete set null,
  account_id uuid references public.user_game_accounts(id) on delete set null,
  status_code integer,
  error_code text not null default '',
  latency_ms integer,
  cache_status text not null default '',
  rate_limited boolean not null default false,
  created_at timestamptz not null default now()
);

create index if not exists idx_provider_game_support_game on public.provider_game_support(game_id);
create index if not exists idx_user_game_accounts_user on public.user_game_accounts(user_id);
create index if not exists idx_user_game_accounts_lookup on public.user_game_accounts(provider_id, game_id, platform, provider_account_id);
create index if not exists idx_game_profile_snapshots_account on public.game_profile_snapshots(user_game_account_id, fetched_at desc);
create index if not exists idx_manual_game_profiles_user on public.manual_game_profiles(user_id);
create index if not exists idx_sync_jobs_status_schedule on public.sync_jobs(status, scheduled_at);
create index if not exists idx_provider_request_events_provider_time on public.provider_request_events(provider_id, created_at desc);

alter table public.game_providers enable row level security;
alter table public.provider_game_support enable row level security;
alter table public.user_game_accounts enable row level security;
alter table public.linked_authorizations enable row level security;
alter table public.game_profile_snapshots enable row level security;
alter table public.manual_game_profiles enable row level security;
alter table public.sync_jobs enable row level security;
alter table public.provider_request_events enable row level security;

drop policy if exists "providers readable" on public.game_providers;
drop policy if exists "provider game support readable" on public.provider_game_support;
drop policy if exists "users manage own game accounts" on public.user_game_accounts;
drop policy if exists "users manage own linked authorizations" on public.linked_authorizations;
drop policy if exists "users read own game snapshots" on public.game_profile_snapshots;
drop policy if exists "users manage own manual game profiles" on public.manual_game_profiles;
drop policy if exists "users read own sync jobs" on public.sync_jobs;
drop policy if exists "public game accounts readable when visible" on public.user_game_accounts;
drop policy if exists "public snapshots readable when account visible" on public.game_profile_snapshots;

create policy "providers readable" on public.game_providers for select using (true);
create policy "provider game support readable" on public.provider_game_support for select using (true);

create policy "users manage own game accounts" on public.user_game_accounts
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy "users manage own linked authorizations" on public.linked_authorizations
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy "users read own game snapshots" on public.game_profile_snapshots
  for select using (
    exists (
      select 1 from public.user_game_accounts uga
      where uga.id = game_profile_snapshots.user_game_account_id
        and uga.user_id = auth.uid()
    )
  );

create policy "users manage own manual game profiles" on public.manual_game_profiles
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy "users read own sync jobs" on public.sync_jobs
  for select using (
    exists (
      select 1 from public.user_game_accounts uga
      where uga.id = sync_jobs.account_id
        and uga.user_id = auth.uid()
    )
  );

create policy "public game accounts readable when visible" on public.user_game_accounts
  for select using (visibility = 'public');

create policy "public snapshots readable when account visible" on public.game_profile_snapshots
  for select using (
    exists (
      select 1 from public.user_game_accounts uga
      where uga.id = game_profile_snapshots.user_game_account_id
        and uga.visibility = 'public'
    )
  );

insert into public.game_providers (provider_key, display_name, enabled, configuration_status, health_status)
values
  ('tracker-network', 'Tracker Network', true, 'env_required', 'unknown'),
  ('steam', 'Steam', false, 'env_required', 'unknown'),
  ('riot', 'Riot Games', false, 'approval_required', 'unknown'),
  ('bungie', 'Bungie.net', false, 'env_required', 'unknown'),
  ('manual', 'Manual Profile', true, 'configured', 'healthy')
on conflict (provider_key) do update set
  display_name = excluded.display_name,
  updated_at = now();

insert into public.provider_game_support (
  provider_id, game_id, capabilities, enabled, requires_approval, supported_platforms, supported_regions
)
select gp.id, 'apex-legends',
  '["publicIdentifierLookup", "profile", "rank", "aggregateStats"]'::jsonb,
  true,
  true,
  '["origin", "xbl", "psn"]'::jsonb,
  '[]'::jsonb
from public.game_providers gp
where gp.provider_key = 'tracker-network'
on conflict (provider_id, game_id) do update set
  capabilities = excluded.capabilities,
  enabled = excluded.enabled,
  requires_approval = excluded.requires_approval,
  supported_platforms = excluded.supported_platforms,
  updated_at = now();

insert into public.provider_game_support (
  provider_id, game_id, capabilities, enabled, requires_approval, supported_platforms, supported_regions
)
select gp.id, g.id,
  '["manualProfile"]'::jsonb,
  true,
  false,
  coalesce(nullif(g.supported_platforms, '[]'::jsonb), g.platforms),
  '[]'::jsonb
from public.game_providers gp
cross join public.games g
where gp.provider_key = 'manual'
on conflict (provider_id, game_id) do update set
  capabilities = excluded.capabilities,
  enabled = excluded.enabled,
  supported_platforms = excluded.supported_platforms,
  updated_at = now();
