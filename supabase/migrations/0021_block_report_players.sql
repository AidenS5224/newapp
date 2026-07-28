create table if not exists public.player_reports (
  id uuid primary key default gen_random_uuid(),
  reporter_profile_id uuid not null references public.profiles(id) on delete cascade,
  reported_profile_id uuid not null references public.profiles(id) on delete cascade,
  reason text not null check (
    reason in (
      'Harassment',
      'Spam',
      'Hate or abusive content',
      'Impersonation',
      'Other'
    )
  ),
  description text not null default '',
  created_at timestamptz not null default now(),
  check (reporter_profile_id <> reported_profile_id)
);

alter table public.player_reports enable row level security;

drop policy if exists "users create own reports" on public.player_reports;

create policy "users create own reports"
on public.player_reports
for insert
to authenticated
with check (
  auth.uid() = reporter_profile_id
  and reporter_profile_id <> reported_profile_id
);

create or replace function public.is_conversation_blocked(
  target_conversation_id uuid
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  target_type text;
begin
  select conversation_type
  into target_type
  from public.conversations
  where id = target_conversation_id;

  if target_type <> 'direct' then
    return false;
  end if;

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

grant execute
on function public.is_conversation_blocked(uuid)
to authenticated;

create or replace function public.block_profile(
  target_profile_id uuid
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  current_profile_id uuid := auth.uid();
begin
  if current_profile_id is null then
    raise exception 'sign in required';
  end if;

  if target_profile_id is null or target_profile_id = current_profile_id then
    raise exception 'cannot block yourself';
  end if;

  delete from public.connections c
  where (
    c.from_profile_id = current_profile_id
    and c.to_profile_id = target_profile_id
  ) or (
    c.from_profile_id = target_profile_id
    and c.to_profile_id = current_profile_id
  );

  insert into public.blocked_profiles (
    blocker_profile_id,
    blocked_profile_id
  )
  values (
    current_profile_id,
    target_profile_id
  )
  on conflict (blocker_profile_id, blocked_profile_id) do nothing;
end;
$$;

grant execute
on function public.block_profile(uuid)
to authenticated;

create or replace function public.unblock_profile(
  target_profile_id uuid
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  current_profile_id uuid := auth.uid();
begin
  if current_profile_id is null then
    raise exception 'sign in required';
  end if;

  if target_profile_id is null or target_profile_id = current_profile_id then
    raise exception 'choose another player';
  end if;

  delete from public.blocked_profiles bp
  where bp.blocker_profile_id = current_profile_id
    and bp.blocked_profile_id = target_profile_id;
end;
$$;

grant execute
on function public.unblock_profile(uuid)
to authenticated;

create or replace function public.has_blocked_profile(
  target_profile_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.blocked_profiles bp
    where bp.blocker_profile_id = auth.uid()
      and bp.blocked_profile_id = target_profile_id
  );
$$;

grant execute
on function public.has_blocked_profile(uuid)
to authenticated;

create or replace function public.can_start_direct_message(
  target_profile_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select public.are_accepted_friends(auth.uid(), target_profile_id)
    and not public.is_profile_pair_blocked(auth.uid(), target_profile_id);
$$;

grant execute
on function public.can_start_direct_message(uuid)
to authenticated;

create or replace function public.prevent_ineligible_direct_participant()
returns trigger
language plpgsql
set search_path = public
as $$
declare
  target_conversation public.conversations;
  existing_member_count integer;
  existing_member_id uuid;
begin
  select *
  into target_conversation
  from public.conversations
  where id = new.conversation_id;

  if target_conversation.id is null then
    raise exception 'conversation not found';
  end if;

  if target_conversation.conversation_type <> 'direct' then
    return new;
  end if;

  select count(*)
  into existing_member_count
  from public.conversation_participants cp
  where cp.conversation_id = new.conversation_id;

  if existing_member_count >= 2 then
    raise exception 'direct conversations can only have two participants';
  end if;

  select cp.profile_id
  into existing_member_id
  from public.conversation_participants cp
  where cp.conversation_id = new.conversation_id
    and cp.profile_id <> new.profile_id
  limit 1;

  if existing_member_id is null then
    return new;
  end if;

  if not public.are_accepted_friends(existing_member_id, new.profile_id) then
    raise exception 'accepted friendship required for direct conversations';
  end if;

  if public.is_profile_pair_blocked(existing_member_id, new.profile_id) then
    raise exception 'blocked players cannot be messaged';
  end if;

  return new;
end;
$$;

drop trigger if exists prevent_ineligible_direct_participant
on public.conversation_participants;

create trigger prevent_ineligible_direct_participant
before insert
on public.conversation_participants
for each row
execute function public.prevent_ineligible_direct_participant();

create or replace function public.report_profile(
  target_profile_id uuid,
  report_reason text,
  report_description text default ''
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  current_profile_id uuid := auth.uid();
  report_id uuid;
begin
  if current_profile_id is null then
    raise exception 'sign in required';
  end if;

  if target_profile_id is null or target_profile_id = current_profile_id then
    raise exception 'cannot report yourself';
  end if;

  if report_reason not in (
    'Harassment',
    'Spam',
    'Hate or abusive content',
    'Impersonation',
    'Other'
  ) then
    raise exception 'choose a valid report reason';
  end if;

  insert into public.player_reports (
    reporter_profile_id,
    reported_profile_id,
    reason,
    description
  )
  values (
    current_profile_id,
    target_profile_id,
    report_reason,
    left(trim(coalesce(report_description, '')), 500)
  )
  returning id into report_id;

  return report_id;
end;
$$;

grant execute
on function public.report_profile(uuid, text, text)
to authenticated;
