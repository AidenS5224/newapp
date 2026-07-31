create table if not exists public.conversation_typing (
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  profile_id uuid not null references public.profiles(id) on delete cascade,
  is_typing boolean not null default false,
  updated_at timestamptz not null default now(),
  expires_at timestamptz not null,
  primary key (conversation_id, profile_id)
);

create index if not exists idx_conversation_typing_conversation_expires
on public.conversation_typing (conversation_id, expires_at);

alter table public.conversation_typing enable row level security;

drop policy if exists "conversation typing readable by members" on public.conversation_typing;
drop policy if exists "members insert own typing state" on public.conversation_typing;
drop policy if exists "members update own typing state" on public.conversation_typing;
drop policy if exists "members delete own typing state" on public.conversation_typing;

create policy "conversation typing readable by members"
on public.conversation_typing
for select
to authenticated
using (
  public.is_conversation_member(conversation_id, auth.uid())
);

create policy "members insert own typing state"
on public.conversation_typing
for insert
to authenticated
with check (
  profile_id = auth.uid()
  and public.is_conversation_member(conversation_id, auth.uid())
);

create policy "members update own typing state"
on public.conversation_typing
for update
to authenticated
using (
  profile_id = auth.uid()
  and public.is_conversation_member(conversation_id, auth.uid())
)
with check (
  profile_id = auth.uid()
  and public.is_conversation_member(conversation_id, auth.uid())
);

create policy "members delete own typing state"
on public.conversation_typing
for delete
to authenticated
using (
  profile_id = auth.uid()
);


create or replace function public.set_conversation_typing(
  target_conversation_id uuid,
  target_is_typing boolean,
  target_expires_at timestamptz
)
returns void
language plpgsql
security invoker
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'authentication required';
  end if;

  if target_conversation_id is null then
    raise exception 'conversation required';
  end if;

  if not public.is_conversation_member(target_conversation_id, auth.uid()) then
    raise exception 'conversation membership required';
  end if;

  insert into public.conversation_typing (
    conversation_id,
    profile_id,
    is_typing,
    updated_at,
    expires_at
  ) values (
    target_conversation_id,
    auth.uid(),
    target_is_typing,
    now(),
    target_expires_at
  )
  on conflict (conversation_id, profile_id)
  do update set
    is_typing = excluded.is_typing,
    updated_at = now(),
    expires_at = excluded.expires_at;
end;
$$;

grant execute on function public.set_conversation_typing(uuid, boolean, timestamptz) to authenticated;

do $$
begin
  alter publication supabase_realtime add table public.conversation_typing;
exception
  when duplicate_object then null;
end $$;
