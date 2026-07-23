create or replace function public.create_chat(target_profile_ids uuid[], chat_title text default null, first_message text default '')
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  current_profile_id uuid := auth.uid();
  clean_targets uuid[];
  target_count integer;
  existing_conversation_id uuid;
  new_conversation_id uuid := gen_random_uuid();
  generated_title text;
begin
  if current_profile_id is null then
    raise exception 'sign in required';
  end if;

  select coalesce(array_agg(distinct target_id), '{}'::uuid[])
  into clean_targets
  from unnest(coalesce(target_profile_ids, '{}'::uuid[])) as target(target_id)
  where target_id is not null
    and target_id <> current_profile_id;

  target_count := coalesce(array_length(clean_targets, 1), 0);
  if target_count = 0 then
    raise exception 'choose at least one player';
  end if;

  if exists (
    select 1
    from unnest(clean_targets) as target(target_id)
    where public.is_profile_pair_blocked(current_profile_id, target_id)
  ) then
    raise exception 'blocked players cannot be messaged';
  end if;

  if exists (
    select 1
    from unnest(clean_targets) as target(target_id)
    where not exists (
      select 1
      from public.connections c
      where c.status = 'accepted'
        and (
          (c.from_profile_id = current_profile_id and c.to_profile_id = target_id)
          or (c.from_profile_id = target_id and c.to_profile_id = current_profile_id)
        )
    )
  ) then
    raise exception 'all chat members must be accepted connections';
  end if;

  if target_count = 1 then
    select c.id
    into existing_conversation_id
    from public.conversations c
    where c.conversation_type = 'direct'
      and exists (
        select 1 from public.conversation_participants cp
        where cp.conversation_id = c.id and cp.profile_id = current_profile_id
      )
      and exists (
        select 1 from public.conversation_participants cp
        where cp.conversation_id = c.id and cp.profile_id = clean_targets[1]
      )
    limit 1;

    if existing_conversation_id is not null then
      return existing_conversation_id;
    end if;
  end if;

  generated_title := nullif(trim(coalesce(chat_title, '')), '');
  if generated_title is null then
    if target_count = 1 then
      select p.handle into generated_title from public.profiles p where p.id = clean_targets[1];
    else
      generated_title := 'Group Chat';
    end if;
  end if;

  insert into public.conversations (id, title, conversation_type, created_by_profile_id)
  values (
    new_conversation_id,
    generated_title,
    case when target_count = 1 then 'direct' else 'group' end,
    current_profile_id
  );

  insert into public.conversation_participants (conversation_id, profile_id, role)
  values (new_conversation_id, current_profile_id, 'owner');

  insert into public.conversation_participants (conversation_id, profile_id, role)
  select new_conversation_id, target_id, 'member'
  from unnest(clean_targets) as target(target_id);

  if nullif(trim(coalesce(first_message, '')), '') is not null then
    insert into public.messages (conversation_id, sender_profile_id, body)
    values (new_conversation_id, current_profile_id, trim(first_message));
  end if;

  return new_conversation_id;
end;
$$;

grant execute on function public.create_chat(uuid[], text, text) to authenticated;

create or replace function public.remove_connection_with(target_profile_id uuid)
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

  delete from public.connections c
  where (
    c.from_profile_id = current_profile_id
    and c.to_profile_id = target_profile_id
  ) or (
    c.from_profile_id = target_profile_id
    and c.to_profile_id = current_profile_id
  );
end;
$$;

grant execute on function public.remove_connection_with(uuid) to authenticated;

create or replace function public.block_profile(target_profile_id uuid)
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
  if current_profile_id = target_profile_id then
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

  insert into public.blocked_profiles (blocker_profile_id, blocked_profile_id)
  values (current_profile_id, target_profile_id)
  on conflict (blocker_profile_id, blocked_profile_id) do nothing;
end;
$$;

grant execute on function public.block_profile(uuid) to authenticated;
