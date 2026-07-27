create table if not exists public.lfg_join_requests (
  id uuid primary key default gen_random_uuid(),
  lfg_post_id uuid not null references public.lfg_posts(id) on delete cascade,
  requester_profile_id uuid not null references public.profiles(id) on delete cascade,
  status text not null default 'pending',
  created_at timestamptz not null default now(),
  unique (lfg_post_id, requester_profile_id)
);

alter table public.lfg_join_requests enable row level security;

update public.lfg_posts
set status = 'open'
where status not in ('open', 'filled', 'closed');

alter table public.lfg_posts
drop constraint if exists lfg_posts_status_check;

alter table public.lfg_posts
add constraint lfg_posts_status_check
check (status in ('open', 'filled', 'closed'));

alter table public.lfg_join_requests
drop constraint if exists lfg_join_requests_status_check;

alter table public.lfg_join_requests
add constraint lfg_join_requests_status_check
check (status in ('pending', 'accepted', 'rejected'));

create or replace function public.lfg_requested_size(
  party_size_text text
)
returns integer
language sql
immutable
as $$
  select max(match[1]::integer)
  from regexp_matches(coalesce(party_size_text, ''), '\d+', 'g') as match;
$$;

create or replace function public.close_lfg_post(
  post_id uuid
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  lfg_row public.lfg_posts;
begin
  select *
  into lfg_row
  from public.lfg_posts
  where id = post_id
  for update;

  if lfg_row.id is null then
    raise exception 'LFG post not found';
  end if;

  if lfg_row.profile_id <> auth.uid() then
    raise exception 'Only the LFG owner can close this post';
  end if;

  if lfg_row.status = 'closed' then
    return;
  end if;

  update public.lfg_posts
  set status = 'closed'
  where id = lfg_row.id
    and status in ('open', 'filled');
end;
$$;

grant execute
on function public.close_lfg_post(uuid)
to authenticated;

create or replace function public.prevent_unavailable_lfg_join_request()
returns trigger
language plpgsql
set search_path = public
as $$
declare
  lfg_row public.lfg_posts;
  requested_size integer;
  accepted_count integer;
begin
  if new.status <> 'pending' then
    return new;
  end if;

  select *
  into lfg_row
  from public.lfg_posts
  where id = new.lfg_post_id;

  if lfg_row.id is null then
    raise exception 'LFG post not found';
  end if;

  if lfg_row.status = 'closed' then
    raise exception 'This LFG post is closed';
  end if;

  if lfg_row.status = 'filled' then
    raise exception 'This LFG post is already filled';
  end if;

  requested_size := public.lfg_requested_size(lfg_row.party_size);

  if requested_size is not null then
    select count(*)
    into accepted_count
    from public.lfg_members
    where lfg_post_id = lfg_row.id;

    if accepted_count >= requested_size then
      update public.lfg_posts
      set status = 'filled'
      where id = lfg_row.id
        and status = 'open';

      raise exception 'This LFG post is already filled';
    end if;
  end if;

  return new;
end;
$$;

drop trigger if exists prevent_unavailable_lfg_join_request
on public.lfg_join_requests;

create trigger prevent_unavailable_lfg_join_request
before insert or update of status
on public.lfg_join_requests
for each row
execute function public.prevent_unavailable_lfg_join_request();

do $$
begin
  if not exists (
    select 1 from pg_policies
    where schemaname = 'public'
      and tablename = 'lfg_join_requests'
      and policyname = 'requesters can create pending lfg requests'
  ) then
    create policy "requesters can create pending lfg requests"
    on public.lfg_join_requests
    for insert
    to authenticated
    with check (
      auth.uid() = requester_profile_id
      and status = 'pending'
      and exists (
        select 1
        from public.lfg_posts
        where lfg_posts.id = lfg_join_requests.lfg_post_id
          and lfg_posts.status = 'open'
          and lfg_posts.profile_id <> auth.uid()
      )
    );
  end if;

  if not exists (
    select 1 from pg_policies
    where schemaname = 'public'
      and tablename = 'lfg_join_requests'
      and policyname = 'requesters and owners can read lfg requests'
  ) then
    create policy "requesters and owners can read lfg requests"
    on public.lfg_join_requests
    for select
    to authenticated
    using (
      requester_profile_id = auth.uid()
      or exists (
        select 1
        from public.lfg_posts
        where lfg_posts.id = lfg_join_requests.lfg_post_id
          and lfg_posts.profile_id = auth.uid()
      )
    );
  end if;

  if not exists (
    select 1 from pg_policies
    where schemaname = 'public'
      and tablename = 'lfg_join_requests'
      and policyname = 'owners can reject lfg requests'
  ) then
    create policy "owners can reject lfg requests"
    on public.lfg_join_requests
    for update
    to authenticated
    using (
      exists (
        select 1
        from public.lfg_posts
        where lfg_posts.id = lfg_join_requests.lfg_post_id
          and lfg_posts.profile_id = auth.uid()
      )
    )
    with check (
      status = 'rejected'
      and exists (
        select 1
        from public.lfg_posts
        where lfg_posts.id = lfg_join_requests.lfg_post_id
          and lfg_posts.profile_id = auth.uid()
      )
    );
  end if;
end $$;

create or replace function public.accept_lfg_join_request(
  request_id uuid
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  request_row public.lfg_join_requests;
  lfg_row public.lfg_posts;
  conversation_uuid uuid;
  requested_size integer;
  accepted_count integer;
begin
  select *
  into request_row
  from public.lfg_join_requests
  where id = request_id
  for update;

  if request_row.id is null then
    raise exception 'Join request not found';
  end if;

  if request_row.status <> 'pending' then
    raise exception 'Join request is not pending';
  end if;

  select *
  into lfg_row
  from public.lfg_posts
  where id = request_row.lfg_post_id
  for update;

  if lfg_row.profile_id <> auth.uid() then
    raise exception 'Only the LFG owner can accept this request';
  end if;

  if lfg_row.status = 'closed' then
    raise exception 'This LFG post is closed';
  end if;

  if lfg_row.status = 'filled' then
    raise exception 'This LFG post is already filled';
  end if;

  requested_size := public.lfg_requested_size(lfg_row.party_size);

  insert into public.lfg_members (
    lfg_post_id,
    profile_id,
    role
  )
  values (
    lfg_row.id,
    lfg_row.profile_id,
    'owner'
  )
  on conflict do nothing;

  if requested_size is not null then
    select count(*)
    into accepted_count
    from public.lfg_members
    where lfg_post_id = lfg_row.id;

    if accepted_count >= requested_size then
      update public.lfg_posts
      set status = 'filled'
      where id = lfg_row.id
        and status = 'open';

      raise exception 'This LFG post is already filled';
    end if;
  end if;

  insert into public.lfg_members (
    lfg_post_id,
    profile_id,
    role
  )
  values (
    lfg_row.id,
    request_row.requester_profile_id,
    'member'
  )
  on conflict do nothing;

  select conversation_id
  into conversation_uuid
  from public.lfg_conversations
  where lfg_post_id = lfg_row.id;

  if conversation_uuid is null then
    insert into public.conversations (
      title,
      conversation_type,
      created_by_profile_id
    )
    values (
      lfg_row.title,
      'group',
      lfg_row.profile_id
    )
    returning id into conversation_uuid;

    insert into public.lfg_conversations (
      lfg_post_id,
      conversation_id
    )
    values (
      lfg_row.id,
      conversation_uuid
    );
  end if;

  insert into public.conversation_participants (
    conversation_id,
    profile_id,
    role
  )
  values
  (
    conversation_uuid,
    lfg_row.profile_id,
    'owner'
  ),
  (
    conversation_uuid,
    request_row.requester_profile_id,
    'member'
  )
  on conflict do nothing;

  update public.lfg_join_requests
  set status = 'accepted'
  where id = request_row.id;

  requested_size := public.lfg_requested_size(lfg_row.party_size);

  if requested_size is not null then
    select count(*)
    into accepted_count
    from public.lfg_members
    where lfg_post_id = lfg_row.id;

    if accepted_count >= requested_size then
      update public.lfg_posts
      set status = 'filled'
      where id = lfg_row.id
        and status = 'open';
    end if;
  end if;

  return conversation_uuid;
end;
$$;

grant execute
on function public.accept_lfg_join_request(uuid)
to authenticated;

do $$
begin
  alter publication supabase_realtime add table public.lfg_posts;
exception
  when duplicate_object then null;
end $$;

do $$
begin
  alter publication supabase_realtime add table public.lfg_join_requests;
exception
  when duplicate_object then null;
end $$;

do $$
begin
  alter publication supabase_realtime add table public.lfg_members;
exception
  when duplicate_object then null;
end $$;
