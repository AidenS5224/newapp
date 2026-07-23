create or replace function public.is_conversation_member(target_conversation_id uuid, target_profile_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
  return exists (
    select 1
    from public.conversation_participants cp
    where cp.conversation_id = target_conversation_id
      and cp.profile_id = target_profile_id
  );
end;
$$;

grant execute on function public.is_conversation_member(uuid, uuid) to authenticated;

drop policy if exists "conversation members readable" on public.conversations;
drop policy if exists "participants readable by members" on public.conversation_participants;
drop policy if exists "participants can read own row" on public.conversation_participants;
drop policy if exists "creator can add participants" on public.conversation_participants;
drop policy if exists "messages readable by members" on public.messages;
drop policy if exists "members send messages" on public.messages;

create policy "conversation members readable" on public.conversations for select using (
  public.is_conversation_member(conversations.id, auth.uid())
);

create policy "participants readable by members" on public.conversation_participants for select using (
  public.is_conversation_member(conversation_participants.conversation_id, auth.uid())
);

create policy "participants can read own row" on public.conversation_participants for select using (
  profile_id = auth.uid()
);

create policy "creator can add participants" on public.conversation_participants for insert with check (
  exists (
    select 1
    from public.conversations c
    where c.id = conversation_participants.conversation_id
      and c.created_by_profile_id = auth.uid()
  )
);

create policy "messages readable by members" on public.messages for select using (
  public.is_conversation_member(messages.conversation_id, auth.uid())
);

create policy "members send messages" on public.messages for insert with check (
  auth.uid() = sender_profile_id
  and public.is_conversation_member(messages.conversation_id, auth.uid())
);
