create or replace function public.send_friend_request(
  target_profile_id uuid
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  current_profile_id uuid := auth.uid();
  existing_connection public.connections;
  new_connection_id uuid;
begin
  if current_profile_id is null then
    raise exception 'sign in required';
  end if;

  if target_profile_id is null or target_profile_id = current_profile_id then
    raise exception 'choose another player';
  end if;

  if public.is_profile_pair_blocked(current_profile_id, target_profile_id) then
    raise exception 'blocked players cannot be added';
  end if;

  select *
  into existing_connection
  from public.connections c
  where (
    c.from_profile_id = current_profile_id
    and c.to_profile_id = target_profile_id
  ) or (
    c.from_profile_id = target_profile_id
    and c.to_profile_id = current_profile_id
  )
  order by c.created_at desc
  limit 1
  for update;

  if existing_connection.id is not null
    and existing_connection.status in ('pending', 'accepted') then
    return existing_connection.id;
  end if;

  delete from public.connections c
  where (
    c.from_profile_id = current_profile_id
    and c.to_profile_id = target_profile_id
  ) or (
    c.from_profile_id = target_profile_id
    and c.to_profile_id = current_profile_id
  );

  insert into public.connections (
    from_profile_id,
    to_profile_id,
    message,
    status
  )
  values (
    current_profile_id,
    target_profile_id,
    'Want to squad up?',
    'pending'
  )
  returning id into new_connection_id;

  return new_connection_id;
end;
$$;

grant execute
on function public.send_friend_request(uuid)
to authenticated;

create or replace function public.respond_friend_request(
  request_id uuid,
  response_status text
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  current_profile_id uuid := auth.uid();
  request_row public.connections;
begin
  if current_profile_id is null then
    raise exception 'sign in required';
  end if;

  if response_status not in ('accepted', 'rejected') then
    raise exception 'invalid friend request response';
  end if;

  select *
  into request_row
  from public.connections c
  where c.id = request_id
  for update;

  if request_row.id is null then
    raise exception 'friend request not found';
  end if;

  if request_row.to_profile_id <> current_profile_id then
    raise exception 'only the recipient can respond to this friend request';
  end if;

  if request_row.status <> 'pending' then
    raise exception 'friend request is not pending';
  end if;

  update public.connections
  set status = response_status
  where id = request_row.id;
end;
$$;

grant execute
on function public.respond_friend_request(uuid, text)
to authenticated;

create or replace function public.remove_friendship(
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

grant execute
on function public.remove_friendship(uuid)
to authenticated;

create or replace function public.search_player_profiles(
  search_text text
)
returns setof public.profiles
language sql
stable
security definer
set search_path = public
as $$
  select p.*
  from public.profiles p
  where p.id <> auth.uid()
    and nullif(trim(coalesce(search_text, '')), '') is not null
    and p.display_name ilike '%' || trim(search_text) || '%'
  order by p.display_name asc
  limit 25;
$$;

grant execute
on function public.search_player_profiles(text)
to authenticated;

create or replace function public.prevent_duplicate_active_connection()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  if new.status not in ('pending', 'accepted') then
    return new;
  end if;

  if new.from_profile_id = new.to_profile_id then
    raise exception 'cannot add yourself';
  end if;

  if exists (
    select 1
    from public.connections existing
    where existing.id <> new.id
      and existing.status in ('pending', 'accepted')
      and (
        (
          existing.from_profile_id = new.from_profile_id
          and existing.to_profile_id = new.to_profile_id
        ) or (
          existing.from_profile_id = new.to_profile_id
          and existing.to_profile_id = new.from_profile_id
        )
      )
  ) then
    raise exception 'friend request already exists';
  end if;

  return new;
end;
$$;

drop trigger if exists prevent_duplicate_active_connection
on public.connections;

create trigger prevent_duplicate_active_connection
before insert or update of status
on public.connections
for each row
execute function public.prevent_duplicate_active_connection();

drop policy if exists "create own connection" on public.connections;
drop policy if exists "participants update own connections" on public.connections;
drop policy if exists "respond to incoming connection" on public.connections;

create policy "create own pending connection"
on public.connections
for insert
to authenticated
with check (
  auth.uid() = from_profile_id
  and from_profile_id <> to_profile_id
  and status = 'pending'
  and not public.is_profile_pair_blocked(from_profile_id, to_profile_id)
);

create policy "recipients respond to pending connections"
on public.connections
for update
to authenticated
using (
  auth.uid() = to_profile_id
  and status = 'pending'
)
with check (
  auth.uid() = to_profile_id
  and status in ('accepted', 'rejected')
);
