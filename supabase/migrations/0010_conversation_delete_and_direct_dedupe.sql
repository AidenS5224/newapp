create table if not exists public.direct_conversation_pairs (
  conversation_id uuid primary key references public.conversations(id) on delete cascade,
  profile_low uuid not null references public.profiles(id) on delete cascade,
  profile_high uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  check (profile_low <> profile_high),
  unique (profile_low, profile_high)
);

alter table public.direct_conversation_pairs enable row level security;

with direct_pairs as (
  select
    c.id,
    case when p1.profile_id::text < p2.profile_id::text then p1.profile_id else p2.profile_id end as profile_low,
    case when p1.profile_id::text < p2.profile_id::text then p2.profile_id else p1.profile_id end as profile_high,
    row_number() over (
      partition by
        case when p1.profile_id::text < p2.profile_id::text then p1.profile_id else p2.profile_id end,
        case when p1.profile_id::text < p2.profile_id::text then p2.profile_id else p1.profile_id end
      order by c.updated_at desc, c.created_at desc, c.id desc
    ) as row_number
  from public.conversations c
  join public.conversation_participants p1 on p1.conversation_id = c.id
  join public.conversation_participants p2 on p2.conversation_id = c.id and p1.profile_id::text < p2.profile_id::text
  where c.conversation_type = 'direct'
    and (
      select count(*)
      from public.conversation_participants cp
      where cp.conversation_id = c.id
    ) = 2
)
delete from public.conversations c
using direct_pairs dp
where c.id = dp.id
  and dp.row_number > 1;

insert into public.direct_conversation_pairs (conversation_id, profile_low, profile_high)
select
  c.id,
  case when p1.profile_id::text < p2.profile_id::text then p1.profile_id else p2.profile_id end,
  case when p1.profile_id::text < p2.profile_id::text then p2.profile_id else p1.profile_id end
from public.conversations c
join public.conversation_participants p1 on p1.conversation_id = c.id
join public.conversation_participants p2 on p2.conversation_id = c.id and p1.profile_id::text < p2.profile_id::text
where c.conversation_type = 'direct'
  and (
    select count(*)
    from public.conversation_participants cp
    where cp.conversation_id = c.id
  ) = 2
on conflict (profile_low, profile_high) do nothing;

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
  low_profile_id uuid;
  high_profile_id uuid;
begin
  if current_profile_id is null then
    raise exception 'sign in required';
  end if;

  select coalesce(array_agg(distinct target_id order by target_id), '{}'::uuid[])
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
    if current_profile_id::text < clean_targets[1]::text then
      low_profile_id := current_profile_id;
      high_profile_id := clean_targets[1];
    else
      low_profile_id := clean_targets[1];
      high_profile_id := current_profile_id;
    end if;

    select dcp.conversation_id
    into existing_conversation_id
    from public.direct_conversation_pairs dcp
    join public.conversations c on c.id = dcp.conversation_id
    where dcp.profile_low = low_profile_id
      and dcp.profile_high = high_profile_id
    order by c.updated_at desc
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

  if target_count = 1 then
    insert into public.direct_conversation_pairs (conversation_id, profile_low, profile_high)
    values (new_conversation_id, low_profile_id, high_profile_id)
    on conflict (profile_low, profile_high) do update
      set conversation_id = public.direct_conversation_pairs.conversation_id
    returning conversation_id into existing_conversation_id;

    if existing_conversation_id <> new_conversation_id then
      delete from public.conversations where id = new_conversation_id;
      return existing_conversation_id;
    end if;
  end if;

  if nullif(trim(coalesce(first_message, '')), '') is not null then
    insert into public.messages (conversation_id, sender_profile_id, body)
    values (new_conversation_id, current_profile_id, trim(first_message));
  end if;

  return new_conversation_id;
end;
$$;

grant execute on function public.create_chat(uuid[], text, text) to authenticated;

create or replace function public.delete_conversation(target_conversation_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  current_profile_id uuid := auth.uid();
  target_type text;
begin
  if current_profile_id is null then
    raise exception 'sign in required';
  end if;

  if not public.is_conversation_member(target_conversation_id, current_profile_id) then
    raise exception 'not a conversation member';
  end if;

  select conversation_type
  into target_type
  from public.conversations
  where id = target_conversation_id;

  if target_type = 'direct' then
    delete from public.conversations
    where id = target_conversation_id;
    return;
  end if;

  delete from public.conversation_participants
  where conversation_id = target_conversation_id
    and profile_id = current_profile_id;

  if not exists (
    select 1
    from public.conversation_participants
    where conversation_id = target_conversation_id
  ) then
    delete from public.conversations
    where id = target_conversation_id;
  end if;
end;
$$;

grant execute on function public.delete_conversation(uuid) to authenticated;
