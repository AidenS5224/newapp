create table if not exists public.lfg_members (
  lfg_post_id uuid not null
    references public.lfg_posts(id)
    on delete cascade,

  profile_id uuid not null
    references public.profiles(id)
    on delete cascade,

  role text not null default 'member'
    check (role in ('owner', 'member')),

  joined_at timestamptz not null default now(),

  primary key (lfg_post_id, profile_id)
);

create table if not exists public.lfg_conversations (
  lfg_post_id uuid primary key
    references public.lfg_posts(id)
    on delete cascade,

  conversation_id uuid not null unique
    references public.conversations(id)
    on delete cascade,

  created_at timestamptz not null default now()
);

alter table public.lfg_members enable row level security;
alter table public.lfg_conversations enable row level security;

create policy "members can read lfg membership"
on public.lfg_members
for select
to authenticated
using (
  profile_id = auth.uid()
  or exists (
    select 1
    from public.lfg_posts
    where lfg_posts.id = lfg_members.lfg_post_id
      and lfg_posts.profile_id = auth.uid()
  )
);

create policy "members can read lfg conversation link"
on public.lfg_conversations
for select
to authenticated
using (
  exists (
    select 1
    from public.lfg_members
    where lfg_members.lfg_post_id = lfg_conversations.lfg_post_id
      and lfg_members.profile_id = auth.uid()
  )
);

create policy "lfg owner can manage lfg members"
on public.lfg_members
for all
to authenticated
using (
  exists (
    select 1
    from public.lfg_posts
    where lfg_posts.id = lfg_members.lfg_post_id
      and lfg_posts.profile_id = auth.uid()
  )
)
with check (
  exists (
    select 1
    from public.lfg_posts
    where lfg_posts.id = lfg_members.lfg_post_id
      and lfg_posts.profile_id = auth.uid()
  )
);

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

  return conversation_uuid;
end;
$$;

grant execute
on function public.accept_lfg_join_request(uuid)
to authenticated;