create or replace function public.transfer_group_ownership(
  target_conversation_id uuid,
  new_owner_profile_id uuid
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  current_profile_id uuid := auth.uid();
  target_type text;
  owner_count integer;
begin
  if current_profile_id is null then
    raise exception 'sign in required';
  end if;

  if target_conversation_id is null then
    raise exception 'conversation required';
  end if;

  if new_owner_profile_id is null then
    raise exception 'new owner required';
  end if;

  if new_owner_profile_id = current_profile_id then
    raise exception 'choose another member';
  end if;

  select conversation_type
  into target_type
  from public.conversations
  where id = target_conversation_id
  for update;

  if target_type is null then
    raise exception 'conversation not found';
  end if;

  if target_type <> 'group' then
    raise exception 'ownership can only be transferred for groups';
  end if;

  perform 1
  from public.conversation_participants
  where conversation_id = target_conversation_id
  for update;

  if not exists (
    select 1
    from public.conversation_participants
    where conversation_id = target_conversation_id
      and profile_id = current_profile_id
      and role = 'owner'
  ) then
    raise exception 'only the current owner can transfer ownership';
  end if;

  if not exists (
    select 1
    from public.conversation_participants
    where conversation_id = target_conversation_id
      and profile_id = new_owner_profile_id
  ) then
    raise exception 'new owner must be a current group member';
  end if;

  update public.conversation_participants
  set role = 'member'
  where conversation_id = target_conversation_id
    and role = 'owner';

  update public.conversation_participants
  set role = 'owner'
  where conversation_id = target_conversation_id
    and profile_id = new_owner_profile_id;

  update public.conversations
  set created_by_profile_id = new_owner_profile_id
  where id = target_conversation_id;

  select count(*)
  into owner_count
  from public.conversation_participants
  where conversation_id = target_conversation_id
    and role = 'owner';

  if owner_count <> 1 then
    raise exception 'ownership transfer failed';
  end if;
end;
$$;

grant execute
on function public.transfer_group_ownership(uuid, uuid)
to authenticated;
