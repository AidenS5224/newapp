create table if not exists public.blocked_profiles (
  blocker_profile_id uuid not null references public.profiles(id) on delete cascade,
  blocked_profile_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker_profile_id, blocked_profile_id),
  check (blocker_profile_id <> blocked_profile_id)
);

alter table public.blocked_profiles enable row level security;

create or replace function public.is_conversation_blocked(target_conversation_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
  return exists (
    select 1
    from public.conversation_participants blocker
    join public.conversation_participants blocked
      on blocked.conversation_id = blocker.conversation_id
    join public.blocked_profiles bp
      on bp.blocker_profile_id = blocker.profile_id
      and bp.blocked_profile_id = blocked.profile_id
    where blocker.conversation_id = target_conversation_id
  );
end;
$$;

grant execute on function public.is_conversation_blocked(uuid) to authenticated;

create or replace function public.is_profile_pair_blocked(first_profile_id uuid, second_profile_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
  return exists (
    select 1
    from public.blocked_profiles bp
    where (
      bp.blocker_profile_id = first_profile_id
      and bp.blocked_profile_id = second_profile_id
    ) or (
      bp.blocker_profile_id = second_profile_id
      and bp.blocked_profile_id = first_profile_id
    )
  );
end;
$$;

grant execute on function public.is_profile_pair_blocked(uuid, uuid) to authenticated;

drop policy if exists "users manage own blocked profiles" on public.blocked_profiles;
drop policy if exists "create own connection" on public.connections;
drop policy if exists "users can remove own connections" on public.connections;
drop policy if exists "participants update own connections" on public.connections;
drop policy if exists "conversation members readable" on public.conversations;
drop policy if exists "messages readable by members" on public.messages;
drop policy if exists "members send messages" on public.messages;

create policy "users manage own blocked profiles" on public.blocked_profiles
  for all
  using (auth.uid() = blocker_profile_id)
  with check (auth.uid() = blocker_profile_id);

create policy "create own connection" on public.connections for insert with check (
  auth.uid() = from_profile_id
  and not public.is_profile_pair_blocked(from_profile_id, to_profile_id)
);

create policy "users can remove own connections" on public.connections
  for delete
  using (auth.uid() in (from_profile_id, to_profile_id));

create policy "participants update own connections" on public.connections
  for update
  using (auth.uid() in (from_profile_id, to_profile_id))
  with check (auth.uid() in (from_profile_id, to_profile_id));

create policy "conversation members readable" on public.conversations for select using (
  public.is_conversation_member(conversations.id, auth.uid())
  and not public.is_conversation_blocked(conversations.id)
);

create policy "messages readable by members" on public.messages for select using (
  public.is_conversation_member(messages.conversation_id, auth.uid())
  and not public.is_conversation_blocked(messages.conversation_id)
);

create policy "members send messages" on public.messages for insert with check (
  auth.uid() = sender_profile_id
  and public.is_conversation_member(messages.conversation_id, auth.uid())
  and not public.is_conversation_blocked(messages.conversation_id)
);
