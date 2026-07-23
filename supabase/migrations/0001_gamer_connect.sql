create extension if not exists pgcrypto;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  handle text not null unique,
  display_name text not null,
  age integer,
  region text not null default 'Australia',
  timezone text not null default 'AEST',
  platforms jsonb not null default '[]'::jsonb,
  top_games jsonb not null default '[]'::jsonb,
  rank text not null default 'Unranked',
  play_style jsonb not null default '[]'::jsonb,
  availability jsonb not null default '{}'::jsonb,
  bio text not null default '',
  avatar_url text,
  online boolean not null default false,
  stats jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create table if not exists public.profile_private (
  profile_id uuid primary key references public.profiles(id) on delete cascade,
  protected_info jsonb not null default '{}'::jsonb,
  info_stacks jsonb not null default '[]'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.linked_accounts (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.profiles(id) on delete cascade,
  provider text not null,
  account_handle text not null,
  is_private boolean not null default true,
  created_at timestamptz not null default now(),
  unique (profile_id, provider)
);

create table if not exists public.games (
  id text primary key,
  name text not null,
  modes jsonb not null default '[]'::jsonb,
  platforms jsonb not null default '[]'::jsonb,
  crossplay boolean not null default true
);

create table if not exists public.connections (
  id uuid primary key default gen_random_uuid(),
  from_profile_id uuid not null references public.profiles(id) on delete cascade,
  to_profile_id uuid not null references public.profiles(id) on delete cascade,
  message text not null default '',
  status text not null default 'pending' check (status in ('pending', 'accepted', 'rejected')),
  created_at timestamptz not null default now(),
  unique (from_profile_id, to_profile_id)
);

create table if not exists public.lfg_posts (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.profiles(id) on delete cascade,
  game_id text references public.games(id) on delete set null,
  title text not null,
  mode text not null,
  rank_range text not null default '',
  party_size text not null default '',
  starts_at text not null default '',
  status text not null default 'open',
  created_at timestamptz not null default now()
);

create table if not exists public.squads (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  game_id text references public.games(id) on delete set null,
  description text not null default '',
  open_slots integer not null default 0,
  voice_preference text not null default 'Discord',
  schedule text not null default '',
  created_at timestamptz not null default now()
);

create table if not exists public.squad_members (
  squad_id uuid not null references public.squads(id) on delete cascade,
  profile_id uuid not null references public.profiles(id) on delete cascade,
  role text not null default 'member',
  joined_at timestamptz not null default now(),
  primary key (squad_id, profile_id)
);

create table if not exists public.feed_posts (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.profiles(id) on delete cascade,
  post_type text not null default 'post' check (post_type in ('post', 'clip', 'event')),
  game_id text references public.games(id) on delete set null,
  title text not null,
  body text not null,
  media_url text,
  media_type text,
  visibility text not null default 'public' check (visibility in ('public', 'connections', 'private')),
  created_at timestamptz not null default now()
);

create table if not exists public.feed_reactions (
  post_id uuid not null references public.feed_posts(id) on delete cascade,
  profile_id uuid not null references public.profiles(id) on delete cascade,
  reaction text not null default 'like',
  created_at timestamptz not null default now(),
  primary key (post_id, profile_id)
);

create table if not exists public.feed_comments (
  id uuid primary key default gen_random_uuid(),
  post_id uuid not null references public.feed_posts(id) on delete cascade,
  profile_id uuid not null references public.profiles(id) on delete cascade,
  body text not null,
  created_at timestamptz not null default now()
);

create table if not exists public.conversations (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  conversation_type text not null default 'direct' check (conversation_type in ('direct', 'group')),
  created_by_profile_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.conversation_participants (
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  profile_id uuid not null references public.profiles(id) on delete cascade,
  role text not null default 'member',
  joined_at timestamptz not null default now(),
  primary key (conversation_id, profile_id)
);

create table if not exists public.messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  sender_profile_id uuid not null references public.profiles(id) on delete cascade,
  body text not null,
  created_at timestamptz not null default now()
);

alter table public.profiles enable row level security;
alter table public.profile_private enable row level security;
alter table public.linked_accounts enable row level security;
alter table public.games enable row level security;
alter table public.connections enable row level security;
alter table public.lfg_posts enable row level security;
alter table public.squads enable row level security;
alter table public.squad_members enable row level security;
alter table public.feed_posts enable row level security;
alter table public.feed_reactions enable row level security;
alter table public.feed_comments enable row level security;
alter table public.conversations enable row level security;
alter table public.conversation_participants enable row level security;
alter table public.messages enable row level security;

create policy "public profiles are readable" on public.profiles for select using (true);
create policy "users manage own profile" on public.profiles for all using (auth.uid() = id) with check (auth.uid() = id);
create policy "users manage own private profile" on public.profile_private for all using (auth.uid() = profile_id) with check (auth.uid() = profile_id);

create policy "public games are readable" on public.games for select using (true);
create policy "public lfg readable" on public.lfg_posts for select using (true);
create policy "own lfg writable" on public.lfg_posts for all using (auth.uid() = profile_id) with check (auth.uid() = profile_id);
create policy "public squads readable" on public.squads for select using (true);
create policy "squad members readable" on public.squad_members for select using (true);

create policy "public feed readable" on public.feed_posts for select using (visibility = 'public' or auth.uid() = profile_id);
create policy "own feed writable" on public.feed_posts for all using (auth.uid() = profile_id) with check (auth.uid() = profile_id);
create policy "feed reactions readable" on public.feed_reactions for select using (true);
create policy "own reactions writable" on public.feed_reactions for all using (auth.uid() = profile_id) with check (auth.uid() = profile_id);
create policy "feed comments readable" on public.feed_comments for select using (true);
create policy "own comments writable" on public.feed_comments for insert with check (auth.uid() = profile_id);

create policy "connection participants readable" on public.connections for select using (auth.uid() in (from_profile_id, to_profile_id));
create policy "create own connection" on public.connections for insert with check (auth.uid() = from_profile_id);
create policy "respond to incoming connection" on public.connections for update using (auth.uid() = to_profile_id);

create policy "own linked accounts readable" on public.linked_accounts for select using (auth.uid() = profile_id);
create policy "own linked accounts writable" on public.linked_accounts for all using (auth.uid() = profile_id) with check (auth.uid() = profile_id);

create policy "conversation members readable" on public.conversations for select using (
  exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id = conversations.id and cp.profile_id = auth.uid()
  )
);
create policy "conversation creator inserts" on public.conversations for insert with check (auth.uid() = created_by_profile_id);
create policy "participants readable by members" on public.conversation_participants for select using (
  exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id = conversation_participants.conversation_id and cp.profile_id = auth.uid()
  )
);
create policy "creator can add participants" on public.conversation_participants for insert with check (
  exists (
    select 1 from public.conversations c
    where c.id = conversation_participants.conversation_id and c.created_by_profile_id = auth.uid()
  )
);
create policy "messages readable by members" on public.messages for select using (
  exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id = messages.conversation_id and cp.profile_id = auth.uid()
  )
);
create policy "members send messages" on public.messages for insert with check (
  auth.uid() = sender_profile_id and exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id = messages.conversation_id and cp.profile_id = auth.uid()
  )
);
