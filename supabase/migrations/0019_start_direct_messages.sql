create or replace function public.are_accepted_friends(
  profile_a uuid,
  profile_b uuid
)
returns boolean
language sql
stable
set search_path = public
as $$
  select profile_a is not null
    and profile_b is not null
    and profile_a <> profile_b
    and exists (
      select 1
      from public.connections c
      where c.status = 'accepted'
        and (
          (c.from_profile_id = profile_a and c.to_profile_id = profile_b)
          or (c.from_profile_id = profile_b and c.to_profile_id = profile_a)
        )
    );
$$;

create or replace function public.can_start_direct_message(
  target_profile_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select public.are_accepted_friends(auth.uid(), target_profile_id);
$$;

grant execute
on function public.can_start_direct_message(uuid)
to authenticated;

create or replace function public.start_direct_message(
  target_profile_id uuid
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  current_profile_id uuid := auth.uid();
  existing_conversation_id uuid;
  new_conversation_id uuid := gen_random_uuid();
  low_profile_id uuid;
  high_profile_id uuid;
  generated_title text;
begin
  if current_profile_id is null then
    raise exception 'sign in required';
  end if;

  if target_profile_id is null or target_profile_id = current_profile_id then
    raise exception 'choose another player';
  end if;

  if not public.are_accepted_friends(current_profile_id, target_profile_id) then
    raise exception 'accepted friendship required to message this player';
  end if;

  if public.is_profile_pair_blocked(current_profile_id, target_profile_id) then
    raise exception 'blocked players cannot be messaged';
  end if;

  if current_profile_id::text < target_profile_id::text then
    low_profile_id := current_profile_id;
    high_profile_id := target_profile_id;
  else
    low_profile_id := target_profile_id;
    high_profile_id := current_profile_id;
  end if;

  select dcp.conversation_id
  into existing_conversation_id
  from public.direct_conversation_pairs dcp
  join public.conversations c on c.id = dcp.conversation_id
  where dcp.profile_low = low_profile_id
    and dcp.profile_high = high_profile_id
    and c.conversation_type = 'direct'
  order by c.updated_at desc
  limit 1;

  if existing_conversation_id is not null then
    return existing_conversation_id;
  end if;

  select p.handle
  into generated_title
  from public.profiles p
  where p.id = target_profile_id;

  insert into public.conversations (
    id,
    title,
    conversation_type,
    created_by_profile_id
  )
  values (
    new_conversation_id,
    coalesce(nullif(generated_title, ''), 'Direct Chat'),
    'direct',
    current_profile_id
  );

  insert into public.conversation_participants (
    conversation_id,
    profile_id,
    role
  )
  values
  (
    new_conversation_id,
    current_profile_id,
    'owner'
  ),
  (
    new_conversation_id,
    target_profile_id,
    'member'
  );

  insert into public.direct_conversation_pairs (
    conversation_id,
    profile_low,
    profile_high
  )
  values (
    new_conversation_id,
    low_profile_id,
    high_profile_id
  )
  on conflict (profile_low, profile_high) do update
    set conversation_id = public.direct_conversation_pairs.conversation_id
  returning conversation_id into existing_conversation_id;

  if existing_conversation_id <> new_conversation_id then
    delete from public.conversations
    where id = new_conversation_id;

    return existing_conversation_id;
  end if;

  return new_conversation_id;
end;
$$;

grant execute
on function public.start_direct_message(uuid)
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
